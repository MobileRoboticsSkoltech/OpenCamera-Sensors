package net.sourceforge.opencamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class FolderChooserDialog extends DialogFragment {
	private static final String TAG = "FolderChooserFragment";

	private File current_folder = null;
	private AlertDialog dialog = null;
	private ListView list = null;
	
	private class FileWrapper implements Comparable<FileWrapper> {
		private File file = null;
		private boolean is_parent = false;
		
		FileWrapper(File file, boolean is_parent) {
			this.file = file;
			this.is_parent = is_parent;
		}
		
		@Override
		public String toString() {
			if( this.is_parent )
				return "Parent Folder";
			return file.getName();
		}
		
        @SuppressLint("DefaultLocale")
		@Override
        public int compareTo(FileWrapper o) {
        	if( this.is_parent )
        		return -1;
        	else if( o.isParent() )
        		return 1;
	        return this.file.getName().toLowerCase().compareTo(o.getFile().getName().toLowerCase()); 
        } 

        File getFile() {
			return file;
		}
        
        private boolean isParent() {
        	return is_parent;
        }
	}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreateDialog");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
		String folder_name = sharedPreferences.getString("preference_save_location", "OpenCamera");
		current_folder = MainActivity.getImageFolder(folder_name);

		list = new ListView(getActivity());
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if( MyDebug.LOG )
					Log.d(TAG, "onItemClick: " + position);
				FileWrapper file_wrapper = (FileWrapper) parent.getItemAtPosition(position);
				if( MyDebug.LOG )
					Log.d(TAG, "clicked: " + file_wrapper.toString());
				File file = file_wrapper.getFile();
				if( MyDebug.LOG )
					Log.d(TAG, "file: " + file.toString());
				current_folder = file;
				refreshList();
			}
		});
		dialog = new AlertDialog.Builder(getActivity())
	        //.setIcon(R.drawable.alert_dialog_icon)
	        .setView(list)
	        .setPositiveButton("Use Folder",
	            /*new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	    				if( MyDebug.LOG )
	    					Log.d(TAG, "choose folder: " + current_folder.toString());
	    				useFolder();
	                }
	            }*/
        		null
	        )
	        .setNegativeButton(android.R.string.cancel, null)
	        .create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
		    @Override
		    public void onShow(DialogInterface dialog_interface) {
		        Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		        b.setOnClickListener(new View.OnClickListener() {
		            @Override
		            public void onClick(View view) {
	    				if( MyDebug.LOG )
	    					Log.d(TAG, "choose folder: " + current_folder.toString());
	    				if( useFolder() ) {
	    					dialog.dismiss();
	    				}
		            }
		        });
		    }
		});

		refreshList();
        return dialog;
    }
    
    private void refreshList() {
		File [] files = current_folder.listFiles();
		List<FileWrapper> listed_files = new ArrayList<FileWrapper>();
		if( current_folder.getParentFile() != null )
			listed_files.add(new FileWrapper(current_folder.getParentFile(), true));
		for(int i=0;i<files.length;i++) {
			File file = files[i];
			if( file.isDirectory() ) {
				listed_files.add(new FileWrapper(file, false));
			}
		}
		Collections.sort(listed_files);

		ArrayAdapter<FileWrapper> adapter = new ArrayAdapter<FileWrapper>(this.getActivity(), android.R.layout.simple_list_item_1, listed_files);
        list.setAdapter(adapter);
        
        //dialog.setTitle(current_folder.getName());
        dialog.setTitle(current_folder.getAbsolutePath());
    }
    
    private boolean canWrite() {
    	try {
    		if( this.current_folder.canWrite() )
    			return true;
    	}
    	catch(Exception e) {
    	}
    	return false;
    }

    private boolean useFolder() {
		if( canWrite() ) {
        	File base_folder = MainActivity.getBaseFolder();
        	String new_save_location = current_folder.getAbsolutePath();
        	if( current_folder.getParentFile() != null && current_folder.getParentFile().equals(base_folder) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "parent folder is base folder");
				new_save_location = current_folder.getName();
        	}
			if( MyDebug.LOG )
				Log.d(TAG, "new_save_location: " + new_save_location);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("preference_save_location", new_save_location);
			editor.apply();
			return true;
		}
		else {
			Toast.makeText(getActivity(), "Can't write to this folder", Toast.LENGTH_SHORT).show();
		}
		return false;
    }

    @Override
    public void onResume() {
    	super.onResume();
    	refreshList();
    }
}
