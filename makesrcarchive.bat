rd /S /Q c:\temp\opencamerasrc\

mkdir c:\temp\opencamerasrc\

set src="."
set dst="c:\temp\opencamerasrc"

copy %src%\.gitignore %dst%
copy %src%\build.gradle %dst%
copy %src%\gradlew %dst%
copy %src%\gradlew.bat %dst%
copy %src%\makesrcarchive.bat %dst%
copy %src%\opencamera_source.txt %dst%
copy %src%\gpl-3.0.txt %dst%
copy %src%\settings.gradle %dst%

mkdir %dst%\app
mkdir %dst%\app\src
xcopy %src%\app\src %dst%\app\src /E /Y
copy %src%\app\build.gradle %dst%\app\

mkdir %dst%\gradle
xcopy %src%\gradle %dst%\gradle /E /Y

REM mkdir %dst%\_docs
REM xcopy %src%\_docs %dst%\_docs /E /Y

REM exit
