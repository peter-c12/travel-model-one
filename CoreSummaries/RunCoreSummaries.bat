::
::  Parameters (environment variables):
::   RUN_NAME    : run name (e.g. 2010_04_ZZZ).  Often part of RUN_DIR. Used for TARGET_DIR.
::
::  Uses the RUN_NAME to set ITER, RUN_DIR, POPSYN_HH, POPSYN_PERS, RUN_DESC
::  (Todo: Should these be in a better location?  Like M:\Application\Model One\Model Run Directory.xlsx?
::
:: @echo off
setlocal enabledelayedexpansion

:: Overhead
set R_HOME=C:\Program Files\R\R-3.1.1
set R_USER=mtc
set RDATA=ActiveTransport ActivityPattern AutomobileOwnership CommuteByEmploymentLocation CommuteByIncomeHousehold CommuteByIncomeJob JourneyToWork PerTripTravelTime TimeOfDay TimeOfDay_personsTouring TravelCost TripDistance VehicleMilesTraveled
set CODE_DIR=.\CTRAMP\scripts\CoreSummaries

:: Model run environment variables
set ITER=3
set TARGET_DIR=.

copy %TARGET_DIR%\popsyn\hhFile.*.csv hhFile.csv
copy %TARGET_DIR%\popsyn\personFile.*.csv personFile.csv

:: See if we're missing any summaries
if not exist "%TARGET_DIR%\core_summaries" ( mkdir "%TARGET_DIR%\core_summaries" )

set NEED_SUMMARY=0
for %%X in (%RDATA%) DO (
  if not exist "%TARGET_DIR%\core_summaries\%%X.csv"             ( set /a NEED_SUMMARY+=1 )
)
echo Missing %NEED_SUMMARY% summaries in %TARGET_DIR%\core_summaries

:: If we need to, create the core summaries.
if %NEED_SUMMARY% GTR 0 (
  echo %DATE% %TIME% Running summary script for %RUN_NAME%
  
  rem delete this just in case, so we don't move an old one by accident
  if exist CoreSummaries.html ( del CoreSummaries.html )
  
  rem No .Rprofile -- we set the environment variables here.
  echo "%R_HOME%\bin\x64\Rscript.exe"
  call "%R_HOME%\bin\x64\Rscript.exe" --vanilla "%CODE_DIR%\knit_CoreSummaries.R"
  IF %ERRORLEVEL% GTR 0 goto done
  echo %DATE% %TIME% ...Done
  
  move CoreSummaries.html "%TARGET_DIR%\core_summaries"
  
  rem This will make all the tdes stale
  for %%X in (%RDATA%) DO (
    del "%TARGET_DIR%\core_summaries\%%X.tde"
  )
)
echo.

:: convert the summaries to tde for just this dir
for %%X in ("%TARGET_DIR%\core_summaries\*.rdata") DO (
  if not exist "%TARGET_DIR%\core_summaries\%%~nX.tde" (
    python "%CODE_DIR%\RdataToTableauExtract.py" "%TARGET_DIR%\core_summaries" "%TARGET_DIR%\core_summaries" %%~nxX
    if %ERRORLEVEL% GTR 0 goto done
    
    echo.
  )
)

:: convert the avgload5period.csv
if not exist "%TARGET_DIR%\core_summaries\avgload5period.tde" (
  python "%CODE_DIR%\csvToTableauExtract.py" "%TARGET_DIR%\modelfiles" "%TARGET_DIR%\core_summaries" avgload5period.csv
  if %ERRORLEVEL% GTR 0 goto done
  
  echo.
)

:: convert the transit files
if not exist "%TARGET_DIR%\core_summaries\trnline.tde" (
  FOR %%H in (EA AM MD PM EV) DO (
    FOR %%J in (loc lrf exp hvy com) DO (
      rem walk -> transit -> walk
      python "%CODE_DIR%\csvToTableauExtract.py" --header "name,mode,owner,frequency,line time,line dist,total boardings,passenger miles,passenger hours,path id" --output trnline.tde --join "%CODE_DIR%\tableau\reference-transit-modes.csv" --append "%TARGET_DIR%\modelfiles" "%TARGET_DIR%\core_summaries" trnline%%H_wlk_%%J_wlk.csv
      rem drive -> transit -> walk
      python "%CODE_DIR%\csvToTableauExtract.py" --header "name,mode,owner,frequency,line time,line dist,total boardings,passenger miles,passenger hours,path id" --output trnline.tde --join "%CODE_DIR%\tableau\reference-transit-modes.csv" --append "%TARGET_DIR%\modelfiles" "%TARGET_DIR%\core_summaries" trnline%%H_drv_%%J_wlk.csv      
      rem walk -> transit -> drive
      python "%CODE_DIR%\csvToTableauExtract.py" --header "name,mode,owner,frequency,line time,line dist,total boardings,passenger miles,passenger hours,path id" --output trnline.tde --join "%CODE_DIR%\tableau\reference-transit-modes.csv"  --append "%TARGET_DIR%\modelfiles" "%TARGET_DIR%\core_summaries" trnline%%H_wlk_%%J_drv.csv
    )
  )
)

endlocal

:done