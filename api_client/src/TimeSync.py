import numpy as np
from scipy.signal import correlate as cor
from scipy.interpolate import interp1d, CubicSpline

class TimeSync2:
    """
    A class used to get time delay between two clocks by gyroscope measurements
    ...
    Attributes
    ----------
    xx1 : numpy array of shape (N,3)
        3D angular velocities of the first gyro. for N timestamps
    xx2 : numpy array of shape (M,3)
        3D angular velocities of the second gyro. for M timestamps
    t1 : numpy array of shape (N,)
        timestamps of the first gyro. measurements
    t2 : numpy array of shape (N,)
        timestamps of the second gyro. measurements
    do_resample : bool
        flag to do resampling of angular velocities to equal and constant time grids
        If False then timestamps are used only for estimation of sampling period
    Methods
    -------
    #TOBEDONE
    """
    def __init__(self, xx1, xx2, t1, t2, do_resample=True,):
        self.xx1 = xx1
        self.xx2 = xx2
        self.t1 = t1
        self.t2 = t2
        self.do_resample = do_resample
        self.resample_complete = False
        self.t1_new = None
        self.t2_new = None
        self.xx1_new = None
        self.xx2_new = None
        self.time_delay = None
        self.cor = None
        
    def resample(self, accuracy=1e-3):
        # if... else... can be skipped if data has the same constant data rate
        # then just need `self.x1_new = self.x1` and `self.x2_new = self.x2`
        self.dt = np.min([accuracy,
                     np.mean(np.diff(self.t1)),
                     np.mean(np.diff(self.t2))])
        if self.do_resample:

            self.t1_new = np.arange(self.t1[0], self.t1[-1] + self.dt, self.dt)
            self.t2_new = np.arange(self.t2[0], self.t2[-1] + self.dt, self.dt)

            def interp(t_old, f_old, t_new, kind='cubic'):
                func = interp1d(t_old, f_old, kind=kind, axis=0, bounds_error=False, fill_value=(0,0))
                f_new = func(t_new)
                return f_new

            self.xx1_new = interp(self.t1, self.xx1, self.t1_new)
            self.xx2_new = interp(self.t2, self.xx2, self.t2_new)
        else:
            self.t1_new = self.t1
            self.t2_new = self.t2
            self.xx1_new = self.xx1
            self.xx2_new = self.xx2

        self.resample_complete = True
        
    def obtain_delay(self):
        assert self.resample_complete == True, 'resample() has not called yet'
        
        # Obtain initial index of argmax of cross-cor. Related to initial estimation of time delay
        def get_initial_index(self):
            x1_temp = np.linalg.norm(self.xx1_new, axis=1)
            x2_temp = np.linalg.norm(self.xx2_new, axis=1)
            cross_cor = cor(x2_temp, x1_temp)
            index_init = np.argmax(cross_cor)
            return cross_cor, index_init
        
        # Correction of index numbering
        shift = - self.xx1_new.shape[0] + 1
        # Cross-cor. estimation
        cross_cor, index_init = get_initial_index(self)
        index_init = index_init + shift

        #print(index_init)
        # Rearrangement of data before calibration
        if index_init > 0:
            xx1_temp = self.xx1_new[:-index_init]
            xx2_temp = self.xx2_new[index_init:]
        elif index_init < 0:
            xx1_temp = self.xx1_new[-index_init:]
            xx2_temp = self.xx2_new[:index_init]
        else:
            xx1_temp = self.xx1_new
            xx2_temp = self.xx2_new
        size = min(xx1_temp.shape[0], xx2_temp.shape[0])
        xx1_temp = xx1_temp[:size]
        xx2_temp = xx2_temp[:size]
        #print(xx1_temp.shape, xx2_temp.shape)
        
        # Calibration
        self.M = np.matmul( np.matmul(xx2_temp.T, xx1_temp), np.linalg.inv(np.matmul(xx1_temp.T, xx1_temp)))
        self.xx1_new = np.matmul(self.M, self.xx1_new.T).T
        
        # Cross-cor. reestimation
        cross_cor, index_init = get_initial_index(self)

        # Cross-cor. based cubic spline coefficients
        cubic_spline = CubicSpline(np.arange(cross_cor.shape[0]), cross_cor, bc_type='natural')
        coefs = cubic_spline.c[:,index_init]
        # Check cubic spline derivative sign...
        order = coefs.shape[0] - 1
        derivative = coefs[-2]
        # ... and redefine initial index of cross-cor. if needed
        if derivative < 0:
            index_init -= 1
            coefs = cubic_spline.c[:,index_init]
        
        # Solve qudratic equation to obtain roots
        res = np.roots([(order-i)*coefs[i] for i in range(order)])
        # Choose solution from roots. 
        if sum((order-i)*coefs[i]*((res[0]+res[1])/2)**(order-i-1) for i in range(order)) < 0:
            res = np.min(res)
        else:
            res = np.max(res)
        
        self.cor = cross_cor
        # Get final time delay between starts of tracks
        self.time_delay = (index_init + shift + res) * self.dt