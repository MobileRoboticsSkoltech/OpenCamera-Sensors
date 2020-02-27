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
copy %src%\google_material_design_icons_LICENSE-2.0.txt %dst%
copy %src%\settings.gradle %dst%

mkdir %dst%\app
mkdir %dst%\app\src
xcopy %src%\app\src %dst%\app\src /E /Y
copy %src%\app\build.gradle %dst%\app\

mkdir %dst%\gradle
xcopy %src%\gradle %dst%\gradle /E /Y

REM We copy the inspectionProfiles as this stores which Android inspection warnings/errors we've disabled; although
REM note this isn't part of the Git repository, due lots of other files in .idea/ that we don't want to be part of the
REM project.
mkdir %dst%\.idea
mkdir %dst%\.idea\inspectionProfiles
xcopy %src%\.idea\inspectionProfiles %dst%\.idea\inspectionProfiles /E /Y

mkdir %dst%\_docs
REM xcopy %src%\_docs %dst%\_docs /E /Y
copy %src%\_docs\credits.html %dst%\_docs
copy %src%\_docs\devices.html %dst%\_docs
copy %src%\_docs\help.html %dst%\_docs
copy %src%\_docs\history.html %dst%\_docs
copy %src%\_docs\index.html %dst%\_docs
copy %src%\_docs\privacy_oc.html %dst%\_docs
copy %src%\_docs\stylesheet.css %dst%\_docs

REM exit
