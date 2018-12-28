package com.pb.models.ctramp.jppf;

import gnu.cajo.invoke.Remote;
import gnu.cajo.utils.ItemServer;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.UnknownHostException;

import org.apache.log4j.Logger;
import org.jppf.client.JPPFClient;

import com.pb.common.matrix.MatrixType;
import com.pb.common.util.ResourceUtil;
import com.pb.common.calculator.MatrixDataManager;
import com.pb.common.calculator.MatrixDataServerIf;
import com.pb.models.ctramp.CtrampDmuFactoryIf;
import com.pb.models.ctramp.Definitions;
import com.pb.models.ctramp.DestChoiceSize;
import com.pb.models.ctramp.Household;
import com.pb.models.ctramp.HouseholdDataManagerIf;
import com.pb.models.ctramp.HouseholdDataWriter;
import com.pb.models.ctramp.MatrixDataServer;
import com.pb.models.ctramp.MatrixDataServerRmi;
import com.pb.models.ctramp.ModelStructure;
import com.pb.models.ctramp.Person;
import com.pb.models.ctramp.StopFrequencyDMU;
import com.pb.models.ctramp.TazDataIf;
import com.pb.models.ctramp.Tour;
import com.pb.models.synpopV3.PopulationSynthesizer;


// 1.0.1 -          - delivered for PBSJ use in calibrating wor/school location
// 1.0.2 - 12/29/08 - fixed bug in size variable indexing where size was associated with wrong zones.
// 1.0.3 - 12/29/08 - added naming sqlite databse file in proprties file, or ignore SqlService object if name not specified.
// 1.0.4 - 12/30/08 - closed MatrixDataServer at end of runTourBasedModel() when it was started in a separate process on same machine.
// 1.0.5.0 - 03/04/09 - first jppf/household choice model based version sent to PBS&J
// 1.0.5.1 - 03/11/09 - revised - reduced memory usage in HHDataManager, person objects
// 1.0.6.0 - 03/13/09 - added writing disk object file after UWSL and after household choice models, with file designating next model following
//                      the last one run.  Restart can occur from disk object file or directly from running household manager.
//                      Read/write disk object file now happens from HouseholdDataManager, and thus reads/writes from its local file system.
// 1.0.7.0 - 04/06/09 - fixed bugs in work/school location choice, auto ownership, cdap.
//                      initial reconcile of mtc specific code
// 1.0.8.0 - 04/10/09 - fixed bugs in assigning person types and various problems with shadow pricing.
// 1.0.8.2 - 04/13/09 - made the properties for setting number of DO files and number of hhs per file optional if file was not set.
// 1.0.8.3 - 04/13/09 - RunModel.RestartWithModel property = none or missing indicates fresh restart of hh manager and fresh model run
//                      RunModel.RestartWithModel = "mode component" indicates restart from that component.
// 1.0.8.7 - 04/20/09 - Fixed bug where models referred to wrong autos value - should have been autos from AO mode result value.
// 1.0.8.8 - 04/23/09 - Fixed bug in CDAP model; added reporting household object attributes and their attributes for trace households.
// 1.0.8.9 - 04/24/09 - Changed save methods to use PrintWriter instead of FileWriter to take care of CR/LF issue.
// 1.0.8.10 - 04/27/09 - Added 6+ person CDAP choice from fixed proportions.  Changed UEC to zero out scalar table (token values) for each decision maker.
// 1.0.8.11 - 04/28/09 - Corrected 6+ person CDAP choice from fixed proportions.  Cumulative probabilities were not calculated correctly.
// 1.0.8.12 - 05/01/09 - Corrected At-work purpose naming.  Removed Scheduler, replaced by Person.windows[].  Trace logging through At-work mode choice.
// 1.0.8.13 - 05/05/09 - Corrected purpose naming for stop freq model.  Added writing text files at end.
// 1.0.8.14 - 05/06/09 - Added purpose naming/indexing for stop loc, trip mode choice models.  Corrected writing database tables at end.
// 1.0.8.15 - 05/14/09 - Corrected bugs in IMTF and IMTOD; enhanced logging - including new IMTOD log file
// 1.0.8.16 - 05/15/09 - Corrected bugs in DMU methods and in spreadsheets related to reviews for ">=, <" and "( )".
// 1.0.8.17 - 05/20/09 - Added more logging for specific models, separated joint from individual tours in output file/tables.
// 1.0.8.18 - 05/20/09 - fixed output file/tables.
// 1.0.8.19 - 05/22/09 - corrected setting orig/dest/stop indices in code and using correct indices in UEC spreadsheets.
// 1.0.8.20 - 05/28/09 - added parking loction choice model; changed all the DMU objects (project specific) to implement VariableTable
// 1.0.8.21 - 05/30/09 - added changes to support object initializtion methods - changed where residual time windows were determined
// 1.0.8.22 - 06/02/09 - changed model restat options - possible values for restart are now: none, uwsl, ao, imtf, jtf, inmtf, stf
// 1.0.8.23 - 06/15/09 - reads number of global iterations; revised logging and fixed many bugs in model flow and in UECs;
//                       reduced number of logsum calculations for departure time choice models; fixed tour order when scheduling
//                       for persons with 2 work or school tours.
//1.0.8.24 - 06/16/09  - improved logging for joint tours and fixed bugs in person overlaps and scheduling
//1.0.8.25 - 06/23/09  - added improved logging for individual non-mandatory tours and revised scheduling procedure for multiple indiv non-man tours.
//                       added correct sequencing for mandatory tour scheduling and handling muliple tours 
//1.0.8.26 - 06/25/09  - changing scheduling to allow more than one tour to overlap at ends of time windows.  Moved subtour models after indiv. non-man. models.
//1.0.8.27 - 06/26/09  - changed parsing shadow price name to get the iteration index from after the last "_", before the ".". 
//1.0.8.29 - 07/02/09  - refactored trip choice models and added better logging, revised parking location choice DMU method, added option to
//                       save or not the tour mode choice utlities and probabilities in the tour files, added command line arguments
//                       to optioanlly override iteration=1 and samplerate=1.0, and implemended running model for sample of households.
//1.0.8.30 - 07/08/09  - added method to indicate if tour was joint tour for non-mandatory DC, SLC, and PLC - revised UEC files as well.
//                     - fixed bug in restarting at inmtf due to corrupted random number sequence.  Added seed for sorting for sampleRate proportion of households. 
//1.0.8.31 - 08/10/09  - moved instantiation of jppf client up in code so a single client is used to submit all tasks - fixes duplicate skim matrices in memory issue.
//                     - debugging related to running on 16 core machines.
//1.0.8.32 - 08/18/09  - corrected bug in at-work departure time choice where wrong model sheet index was used.
//1.0.8.33 - 08/20/09  - corrected bug in TazDataManager when determining if zone is in a particular area type.
//                     - also in ParkinChoiceDMU where parkTazs[] is 0-based, so need alt-1.
//1.0.8.34 - 08/21/09  - corrected more bugs related to inconsistant definitions of area types in UECs / code. 
//1.0.8.35 - 08/21/09  - modified UEC stop parking location to force o, d, s to be alternative specific  
//1.0.8.36 - 08/24/09  - added ms.clear() before usual work/school location choice so matrices get re-read.  
//1.0.8.37 - 08/24/09  - removed ms.clear() - not necessary, because each global iteration is a separate full model run  
//1.0.8.38 - 09/02/09  - added ms.clear32BitMatrixIoServer(), before usual work/school location choice so matrices get re-read - MatrixServer is not restarted each iteration.  
//1.0.8.39 - 09/03/09  - need ms.clear() in DestChoiceModelManager so each remote nodes clears matrixMap.  
//1.0.8.40 - 09/04/09  - should be ms.clear() in CtrampApplication and mdm.clearData()in DestChoiceModelManager so each remote nodes clears matrixMap.
//1.0.8.41 - 09/08/09  - added TableDataSetManager.clearData() call and reset model index values
//1.0.8.42 - 09/11/09  - fixed synchronized bug in HouseholdChoiceModelsManager and added hhs=null in mapTablesToHouseholdObjects.
//1.0.8.43 - 09/12/09  - removed synchronized from the managers return() methods,
//1.0.8.44 - 09/14/09  - removed logging household choice mode component size
//1.0.8.45 - 09/15/09  - changed number of household objects in each "write packet"
//1.0.8.46 - 09/23/09  - set min=512m / max=1024m jvm size for 32 bit jvm. 
//1.0.8.47 - 09/23/09  - set work tour in dmu object for at-work subtour 
//1.0.8.48 - 09/25/09  - changed MatrixDataServer to remove storage of matrix objects and allow it to server as an interface to 32 JVM. 
//1.0.8.49 - 10/01/09  - reverted to old MatrixDataServer and fixed issues in stop generation and writing separate trip files. 
//1.0.8.50 - 10/02/09  - changed MatrixType to implement hashCode() and equals() correctly, since it is used as a Hashtable key. 
//1.0.8.51 - 10/05/09  - corrected bug in reporting number of stops on tours in tour files 
//                     - corrected bug in passing null instead tour (and subtour) objects to sample of alternatives choice models
//                     - in non-mandatory destination choice models.
//1.0.8.52 - 10/07/09  - revisions to parking location choice model to support changes in UEC and limiting model to CBD zones with parking capacity. 
//1.0.8.53 - 10/07/09  - added synchronized blocks to console watcher in DosCommand so stream gobbler threads wouldn't append and delete buffer at same time. 
//1.0.8.54 - 10/12/09  - fixed bug in log reports for destination choice where sample alternatives and utilities were not aligned correctly in reports. 
//1.0.8.55 - 10/13/09  - changed stop location to stratify models by tour purpose instead of stop purpose. 
//1.0.8.56 - 10/15/09  - changed stop frequency model to process joint tours for household, then individual tours by person.  
//1.0.8.57 - 10/20/09  - Household object method to initialize for stf model restart did not clear stop object arrays for joint tours.
//                     - added tour mode to trip records in joint and individual trip output files.
//1.0.8.58 - 10/20/09  - Added thread name to the result message sent back by hh choice model tasks.
//                     - added an option in DosCommand to set a log4j configuration file for the 32 bit jvm. 
//1.0.8.59 - 10/21/09  - Added logging for stop frequency model where tour stops arrays have not been cleared.
//1.0.8.60 - 10/21/09  - Added log report for stop frequency summary table
//1.0.8.61 - 10/30/09  - Added ALWAYS_COMPUTE_PROBABILITIES constant in DestinationSampleOfAlternativesModel to disable use of probabilities cache for testing effectiveness of using cache.
//1.0.8.62 - 10/30/09  - set ALWAYS_COMPUTE_PROBABILITIES constant to false to ensable use of probabilities cache.
//1.0.8.63 - 11/10/09  - modified trip mode choice code and DMUs to support new parking cost calculations.
//1.0.8.64 - 11/18/09  - fixed bug in Person to get if person is <16 and doesn't have M pattern - used in IMTF model.
//1.0.8.65 - 11/30/09  - revised code to include trip mode choice for half-tours with no stops
//1.0.8.66 - 12/17/09  - fixed bug where stop objects were not properly created and added to stop array in tours for half tours without stops
//                     - added ArcHouseholdDataManager2 as an alternate data manager which uses DiskObjectArrays for data storage - still evaluating
//1.0.8.67 - 12/23/09  - changed the id set for trips representing half-tours with no stops to -1 so they can be distinguished in output trip files.
//1.0.8.68 - 1/08/10   - changed looping mechanism in DestChoiceModelManager.clearDcModels() to iterator.  Trying to understand noSuchElement exception.
//                     - copied code from sandag for UtilRmi.method() to retry on socket connection exception
//                     - added option to read number of households to reside in memory from properties file for ArcHouseholdDataManager2.
//1.0.8.69 - 1/11/10   - added modelQueue.clear() in DestChoiceModelManager.clearDcModels() after using iterator to clear probabilities tables.
//1.0.8.70 - 1/15/10   - fixed main() for ArcHouseholdDataManager and ArcHouseholdDataManager2 so port can be set on commmand line.  
//1.0.8.71 - 1/26/10   - fixed bug in writing tour file - number of stops reported.  Fixed bug in getting iteration number for restart of uwsl or ao.
//1.0.8.72 - 1/29/10   - avoiding apparent null dcModel object in modelQueue during DestChoiceModelManager.clearModels().
//1.0.8.73 - 2/03/10   - changed sample of alternatives probabilities caching - probabilitoes now saved by orig taz, purpose,
//                     - assuming households are processed in order by home taz.
//1.0.8.74 - 2/04/10   - changed some hh, person, tour, etc. objects to use byte, short
//                     - changed classes that extend JPPFTask to set class attrinutes not in constructor to transient so they don't get passed
//                     - added DataProvider for HouseholdChoiceModelTaskJppf.
//1.0.8.75 - 2/07/10   - reduced some redundant object creation in setting up household choice model objects.
//                     - fixed bug in sorting households where persons were not connected with households correctly when populating household/person objects.
//1.0.8.76 - 2/18/10   - reduced sice of basic HH objects and members; removed modelstructure; reused sample arrays in SOA choice
//1.0.8.77 - 3/01/10   - corrected bug where wrong time ranges were assigned to periods in ARC tour and trip mode choice dmu objects.
//                     - changed output trip purpose labels in output trip files.
//1.0.8.78 - 3/02/10   - corrected bug where wrong time ranges were assigned to periods in ARC stop soa choice dmu object.
//1.0.8.79 - 6/18/10   - added trip depart time model to StopLocationModeChoiceModel
//1.0.8.80 - 10/8/10   - re-configured restart models, to allow running only mode choice
//1.0.8.81 - 11/27/11  - changed ChoiceModelApplication to use more efficient newmodel.LogitModel, does not use MyLogit 


public class CtrampApplication implements Serializable {

     private Logger logger = Logger.getLogger(CtrampApplication.class);


     public static final String VERSION = "1.0.8.81";


     public static final int MATRIX_DATA_SERVER_PORT = 1171;



     public static final String PROPERTIES_BASE_NAME = "ctramp";
     public static final String PROPERTIES_PROJECT_DIRECTORY = "Project.Directory";

     public static final String SQLITE_DATABASE_FILENAME = "Sqlite.DatabaseFileName";

     public static final String PROPERTIES_RUN_POPSYN                                     = "RunModel.PopulationSynthesizer";
     public static final String PROPERTIES_RUN_WORKSCHOOL_CHOICE                          = "RunModel.UsualWorkAndSchoolLocationChoice";
     public static final String PROPERTIES_RUN_AUTO_OWNERSHIP                             = "RunModel.AutoOwnership";
     public static final String PROPERTIES_RUN_FREE_PARKING_AVAILABLE                     = "RunModel.FreeParking";
     public static final String PROPERTIES_RUN_DAILY_ACTIVITY_PATTERN                     = "RunModel.CoordinatedDailyActivityPattern";
     public static final String PROPERTIES_RUN_INDIV_MANDATORY_TOUR_FREQ                  = "RunModel.IndividualMandatoryTourFrequency";
     public static final String PROPERTIES_RUN_MAND_TOUR_DEP_TIME_AND_DUR                 = "RunModel.MandatoryTourDepartureTimeAndDuration";
     public static final String PROPERTIES_RUN_MAND_TOUR_MODE_CHOICE                      = "RunModel.MandatoryTourModeChoice";
     public static final String PROPERTIES_RUN_AT_WORK_SUBTOUR_FREQ                       = "RunModel.AtWorkSubTourFrequency";
     public static final String PROPERTIES_RUN_AT_WORK_SUBTOUR_LOCATION_CHOICE            = "RunModel.AtWorkSubTourLocationChoice";
     public static final String PROPERTIES_RUN_AT_WORK_SUBTOUR_MODE_CHOICE                = "RunModel.AtWorkSubTourModeChoice";
     public static final String PROPERTIES_RUN_AT_WORK_SUBTOUR_DEP_TIME_AND_DUR           = "RunModel.AtWorkSubTourDepartureTimeAndDuration";
     public static final String PROPERTIES_RUN_JOINT_TOUR_FREQ                            = "RunModel.JointTourFrequency";
     public static final String PROPERTIES_RUN_JOINT_LOCATION_CHOICE                      = "RunModel.JointTourLocationChoice";
     public static final String PROPERTIES_RUN_JOINT_TOUR_MODE_CHOICE                     = "RunModel.JointTourModeChoice";
     public static final String PROPERTIES_RUN_JOINT_TOUR_DEP_TIME_AND_DUR                = "RunModel.JointTourDepartureTimeAndDuration";
     public static final String PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_FREQ              = "RunModel.IndividualNonMandatoryTourFrequency";
     public static final String PROPERTIES_RUN_INDIV_NON_MANDATORY_LOCATION_CHOICE        = "RunModel.IndividualNonMandatoryTourLocationChoice";
     public static final String PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_MODE_CHOICE       = "RunModel.IndividualNonMandatoryTourModeChoice";
     public static final String PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_DEP_TIME_AND_DUR  = "RunModel.IndividualNonMandatoryTourDepartureTimeAndDuration";
     public static final String PROPERTIES_RUN_STOP_FREQUENCY                             = "RunModel.StopFrequency";
     public static final String PROPERTIES_RUN_STOP_LOCATION                              = "RunModel.StopLocation";


     public static final String PROPERTIES_UEC_AUTO_OWNERSHIP                = "UecFile.AutoOwnership";
     public static final String PROPERTIES_UEC_DAILY_ACTIVITY_PATTERN        = "UecFile.CoordinatedDailyActivityPattern";
     public static final String PROPERTIES_UEC_INDIV_MANDATORY_TOUR_FREQ     = "UecFile.IndividualMandatoryTourFrequency";
     public static final String PROPERTIES_UEC_MAND_TOUR_DEP_TIME_AND_DUR    = "UecFile.TourDepartureTimeAndDuration";
     public static final String PROPERTIES_UEC_INDIV_NON_MANDATORY_TOUR_FREQ = "UecFile.IndividualNonMandatoryTourFrequency";

     // TODO eventually move to model-specific structure object
     public static final int TOUR_MODE_CHOICE_WORK_MODEL_UEC_PAGE         = 1;
     public static final int TOUR_MODE_CHOICE_UNIVERSITY_MODEL_UEC_PAGE   = 2;
     public static final int TOUR_MODE_CHOICE_HIGH_SCHOOL_MODEL_UEC_PAGE  = 3;
     public static final int TOUR_MODE_CHOICE_GRADE_SCHOOL_MODEL_UEC_PAGE = 4;

     // TODO eventually move to model-specific model structure object
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_WORK_MODEL_UEC_PAGE     = 1;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_WORK_DEPARTURE_UEC_PAGE = 2;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_WORK_DURATION_UEC_PAGE  = 3;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_WORK_ARRIVAL_UEC_PAGE   = 4;

     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_SCHOOL_MODEL_UEC_PAGE     = 5;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_SCHOOL_DEPARTURE_UEC_PAGE = 6;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_SCHOOL_DURATION_UEC_PAGE  = 7;
     public static final int MANDATORY_TOUR_DEP_TIME_AND_DUR_SCHOOL_ARRIVAL_UEC_PAGE   = 8;

     public static final String PROPERTIES_SCHEDULING_NUMBER_OF_TIME_PERIODS = "Scheduling.NumberOfTimePeriods";
     public static final String PROPERTIES_SCHEDULING_FIRST_TIME_PERIOD = "Scheduling.FirstTimePeriod";

     static final String PROPERTIES_RESTART_WITH_HOUSEHOLD_SERVER = "RunModel.RestartWithHhServer";
     public static final String PROPERTIES_REREAD_MATRIX_DATA_ON_RESTART = "RunModel.RereadMatrixDataOnRestart"; 
     
     static final String PROPERTIES_HOUSEHOLD_DISK_OBJECT_FILE_NAME = "Households.disk.object.base.name";
     static final String PROPERTIES_HOUSEHOLD_DISK_OBJECT_KEY = "Read.HouseholdDiskObjectFile";
     static final String PROPERTIES_TAZ_DISK_OBJECT_FILE_NAME = "TAZ.disk.object.base.name";
     static final String PROPERTIES_TAZ_DISK_OBJECT_KEY = "Read.TAZDiskObjectFile";

     public static final String PROPERTIES_RESULTS_AUTO_OWNERSHIP = "Results.AutoOwnership";
     public static final String PROPERTIES_RESULTS_CDAP = "Results.CoordinatedDailyActivityPattern";
     
     public static final String PROPERTIES_OUTPUT_WRITE_SWITCH = "CTRAMP.Output.WriteToDiskSwitch";
     public static final String PROPERTIES_OUTPUT_HOUSEHOLD_FILE = "CTRAMP.Output.HouseholdFile";
     public static final String PROPERTIES_OUTPUT_PERSON_FILE = "CTRAMP.Output.PersonFile";

     public static final String PROPERTIES_WRITE_DATA_TO_FILE = "Results.WriteDataToFiles";
     public static final String PROPERTIES_WRITE_DATA_TO_DATABASE = "Results.WriteDataToDatabase";

     public static final String PROPERTIES_SAVE_TOUR_MODE_CHOICE_UTILS = "TourModeChoice.Save.UtilsAndProbs";

     public static final String PROPERTIES_WORK_SCHOOL_LOCATION_CHOICE_SHADOW_PRICE_INPUT_FILE = "UsualWorkAndSchoolLocationChoice.ShadowPrice.Input.File";

     public static final String PROPERTIES_NUMBER_OF_GLOBAL_ITERATIONS = "Global.iterations";
     
     public static final String ALT_FIELD_NAME = "a";
     public static final String START_FIELD_NAME = "start";
     public static final String END_FIELD_NAME = "end";
     public static final int START_HOUR = 5;
     public static final int LAST_HOUR = 23;

     
     private static final int NUM_WRITE_PACKETS = 2000;

     

     private boolean restartFromDiskObjectFile = false;
     
     private ResourceBundle resourceBundle;

     private MatrixDataServerIf ms = null;

     private ModelStructure modelStructure;
     private TazDataIf tazDataManager;
     protected String projectDirectory;
     protected String hhDiskObjectFile;
     protected String hhDiskObjectKey;
     protected String tazDiskObjectFile;
     protected String tazDiskObjectKey;

     private HashMap<Integer,HashMap<String,Integer>> cdapByHhSizeAndPattern;
     private HashMap<String,HashMap<String,Integer>> cdapByPersonTypeAndActivity;

     
     
     
     
     public CtrampApplication( ResourceBundle rb ){
         resourceBundle = rb;
         projectDirectory = ResourceUtil.getProperty(resourceBundle, PROPERTIES_PROJECT_DIRECTORY);
     }



     public void setupModels( ModelStructure modelStructure, TazDataIf tazDataManager ){

         this.modelStructure = modelStructure;
         this.tazDataManager = tazDataManager;

         hhDiskObjectFile = ResourceUtil.getProperty(resourceBundle, PROPERTIES_HOUSEHOLD_DISK_OBJECT_FILE_NAME);
         if ( hhDiskObjectFile != null )
             hhDiskObjectFile = projectDirectory + hhDiskObjectFile;
         hhDiskObjectKey = ResourceUtil.getProperty(resourceBundle, PROPERTIES_HOUSEHOLD_DISK_OBJECT_KEY);

         if ( hhDiskObjectKey != null && ! hhDiskObjectKey.equalsIgnoreCase("none") ) {
             restartFromDiskObjectFile = true;
         }

         
         
//         tazDiskObjectFile = ResourceUtil.getProperty(resourceBundle, PROPERTIES_TAZ_DISK_OBJECT_FILE_NAME);
//         if ( tazDiskObjectFile != null )
//             tazDiskObjectFile = projectDirectory + tazDiskObjectFile;
//         tazDiskObjectKey = ResourceUtil.getProperty(resourceBundle, PROPERTIES_TAZ_DISK_OBJECT_KEY);

     }

     
     public void runPopulationSynthesizer( PopulationSynthesizer populationSynthesizer ){

         // run population synthesizer
         boolean runModelPopulationSynthesizer = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_POPSYN);
         if(runModelPopulationSynthesizer){
             populationSynthesizer.runPopulationSynthesizer();
         }

     }


     public void runModels( HouseholdDataManagerIf householdDataManager, CtrampDmuFactoryIf dmuFactory, int globalIterationNumber, float iterationSampleRate ){

         logger.info("Running JPPF CtrampApplication.runModels().");
         
         String matrixServerAddress = "";
         int serverPort = 0;
         try {
             // get matrix server address.  if "none" is specified, no server will be started, and matrix io will ocurr within the current process.
             matrixServerAddress = resourceBundle.getString( "RunModel.MatrixServerAddress" );
             try {
                 // get matrix server port.
                 serverPort = Integer.parseInt( resourceBundle.getString( "RunModel.MatrixServerPort" ) );
             }
             catch ( MissingResourceException e ) {
                 // if no matrix server address entry is found, leave undefined -- it's eithe not needed or show could create an error.
             }
         }
         catch ( MissingResourceException e ) {
             // if no matrix server address entry is found, set to localhost, and a separate matrix io process will be started on localhost.
             matrixServerAddress = "localhost";
             serverPort = MATRIX_DATA_SERVER_PORT;
         }


         MatrixDataServer matrixServer = null;

         try {

             if ( ! matrixServerAddress.equalsIgnoreCase("none") ) {

                 if ( matrixServerAddress.equalsIgnoreCase("localhost") ) {
                     matrixServer = startMatrixServerProcess( matrixServerAddress, serverPort );
                     ms = matrixServer;
                 }
                 else {
                     MatrixDataServerRmi mds = new MatrixDataServerRmi( matrixServerAddress, serverPort, MatrixDataServer.MATRIX_DATA_SERVER_NAME );
                     ms = mds;

                     boolean rereadMatrixDataOnRestart = ResourceUtil.getBooleanProperty( resourceBundle, PROPERTIES_REREAD_MATRIX_DATA_ON_RESTART, true);
                     if (rereadMatrixDataOnRestart) ms.clear();
                     //ms.start32BitMatrixIoServer( MatrixType.TPPLUS );
                     
                     MatrixDataManager mdm = MatrixDataManager.getInstance();
                     mdm.setMatrixDataServerObject( ms );
                     
                 }

             }


         }
         catch ( Exception e ) {

             if ( matrixServerAddress.equalsIgnoreCase("localhost") ) {
                 //matrixServer.stop32BitMatrixIoServer();
             }
             logger.error ( String.format( "exception caught running ctramp model components -- exiting." ), e );
             throw new RuntimeException();

         }
         
         
         

         // run core activity based model for the specified iteration
         runIteration( globalIterationNumber, householdDataManager, dmuFactory );

         
         
         // if a separate process for running matrix data mnager was started, we're done with it, so close it. 
/*         if ( matrixServerAddress.equalsIgnoreCase("localhost") ) {
             matrixServer.stop32BitMatrixIoServer();
         }
         else {
             if ( ! matrixServerAddress.equalsIgnoreCase("none") )
                 ms.stop32BitMatrixIoServer();
         }
*/
     }



     private void runIteration( int iteration, HouseholdDataManagerIf householdDataManager, CtrampDmuFactoryIf dmuFactory ) {
         
         String restartModel = "";
         if ( hhDiskObjectKey != null && ! hhDiskObjectKey.equalsIgnoreCase("none") ) {
             /*
             String doFileName = hhDiskObjectFile + "_" + hhDiskObjectKey;
             householdDataManager.createHhArrayFromSerializedObjectInFile( doFileName, hhDiskObjectKey );
             restartModel = hhDiskObjectKey;
             restartModels ( householdDataManager );
             */
         }
         else {
             restartModel = ResourceUtil.getProperty( resourceBundle, PROPERTIES_RESTART_WITH_HOUSEHOLD_SERVER );
             if ( restartModel == null )
                 restartModel = "none";
             if ( ! restartModel.equalsIgnoreCase("none") )
                 restartModels ( householdDataManager );
         }



         JPPFClient jppfClient = new JPPFClient();
         
         boolean runUsualWorkSchoolChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_WORKSCHOOL_CHOICE);
         if(runUsualWorkSchoolChoiceModel){

             // create an object for calculating destination choice attraction size terms and managing shadow price calculations.
             DestChoiceSize dcSizeObj = new DestChoiceSize( modelStructure, tazDataManager );
             
             // new the usual school and location choice model object
             UsualWorkSchoolLocationChoiceModel usualWorkSchoolLocationChoiceModel = new UsualWorkSchoolLocationChoiceModel(resourceBundle, restartModel, jppfClient, modelStructure, ms, tazDataManager, dcSizeObj, dmuFactory );
    
             // run the model
             logger.info ( "starting usual work and school location choice.");
             usualWorkSchoolLocationChoiceModel.runSchoolAndLocationChoiceModel(householdDataManager);
             logger.info ( "finished with usual work and school location choice.");
             
             logger.info ( "writing work/school location choice results file; may take a few minutes ..." );
             usualWorkSchoolLocationChoiceModel.saveResults( householdDataManager, projectDirectory, iteration );
             logger.info ( String.format("finished writing results file.") );

             usualWorkSchoolLocationChoiceModel = null;
             dcSizeObj = null;

             // write a disk object fle for the householdDataManager, in case we want to restart from the next step.
             if ( hhDiskObjectFile != null ) {
                 /*
                 logger.info ( "writing household disk object file after work/school location choice; may take a long time ..." );
                 String hhFileName = String.format( "%s_%d_ao", hhDiskObjectFile, iteration );
                 householdDataManager.createSerializedHhArrayInFileFromObject( hhFileName, "ao" );
                 logger.info ( String.format("finished writing household disk object file = %s after uwsl; continuing to household choice models ...", hhFileName) );
                 */
             }
             
         }
      
         

         boolean runAutoOwnershipChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AUTO_OWNERSHIP );
         boolean runFreeParkingChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_FREE_PARKING_AVAILABLE );
         boolean runCoordinatedDailyActivityPatternChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_DAILY_ACTIVITY_PATTERN );
         boolean runMandatoryTourFreqChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_MANDATORY_TOUR_FREQ );
         boolean runMandatoryTourTimeOfDayChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_MAND_TOUR_DEP_TIME_AND_DUR );
         boolean runMandatoryTourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_MAND_TOUR_MODE_CHOICE );
         boolean runJointTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_FREQ );
         boolean runJointTourLocationChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_LOCATION_CHOICE );
         boolean runJointTourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_MODE_CHOICE );
         boolean runJointTourDepartureTimeAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_DEP_TIME_AND_DUR );
         boolean runIndivNonManTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_FREQ );
         boolean runIndivNonManTourLocationChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_LOCATION_CHOICE );
         boolean runIndivNonManTourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_MODE_CHOICE );
         boolean runIndivNonManTourDepartureTimeAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_DEP_TIME_AND_DUR );
         boolean runAtWorkSubTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_FREQ );
         boolean runAtWorkSubtourLocationChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_LOCATION_CHOICE );
         boolean runAtWorkSubtourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_MODE_CHOICE );
         boolean runAtWorkSubtourDepartureTimeAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_DEP_TIME_AND_DUR );
         boolean runStopFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_STOP_FREQUENCY );
         boolean runStopLocationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_STOP_LOCATION );


         boolean runHouseholdModels = false;
         if (
             runAutoOwnershipChoiceModel
             || runFreeParkingChoiceModel
             || runCoordinatedDailyActivityPatternChoiceModel
             || runMandatoryTourFreqChoiceModel
             || runMandatoryTourModeChoiceModel
             || runMandatoryTourTimeOfDayChoiceModel
             || runJointTourFrequencyModel 
             || runJointTourLocationChoiceModel
             || runJointTourModeChoiceModel
             || runJointTourDepartureTimeAndDurationModel
             || runIndivNonManTourFrequencyModel 
             || runIndivNonManTourLocationChoiceModel
             || runIndivNonManTourModeChoiceModel
             || runIndivNonManTourDepartureTimeAndDurationModel
             || runAtWorkSubTourFrequencyModel
             || runAtWorkSubtourLocationChoiceModel
             || runAtWorkSubtourModeChoiceModel
             || runAtWorkSubtourDepartureTimeAndDurationModel
             || runStopFrequencyModel
             || runStopLocationModel
                 )
                     runHouseholdModels = true;
         
         
         
         // disk object file is labeled with the next component eligible to be run if model restarted
         String lastComponent = "uwsl";
         String nextComponent = "ao";
         
         if( runHouseholdModels ) {
             
             logger.info ( "starting HouseholdChoiceModelRunner." );     
             HashMap<String, String> propertyMap = ResourceUtil.changeResourceBundleIntoHashMap(resourceBundle);
             HouseholdChoiceModelRunner runner = new HouseholdChoiceModelRunner( propertyMap, jppfClient, restartModel, householdDataManager, ms, modelStructure, tazDataManager, dmuFactory );
             runner.runHouseholdChoiceModels();
    
             if( runAutoOwnershipChoiceModel ){
                 saveAoResults( householdDataManager, projectDirectory );
                 logAoResults( householdDataManager );
                 lastComponent = "ao";
                 nextComponent = "fp";
             }
             
             if( runFreeParkingChoiceModel ){
                 logFpResults( householdDataManager );
                 lastComponent = "fp";
                 nextComponent = "cdap";
             }
             
             if( runCoordinatedDailyActivityPatternChoiceModel ){
                 saveCdapResults( householdDataManager, projectDirectory );
                 logCdapResults( householdDataManager );
                 lastComponent = "cdap";
                 nextComponent = "imtf";
             }
             
             if( runMandatoryTourFreqChoiceModel ){
                 logImtfResults( householdDataManager );
                 lastComponent = "imtf";
                 nextComponent = "imtod";
             }
             
             if( runMandatoryTourTimeOfDayChoiceModel || runMandatoryTourModeChoiceModel ){
                 lastComponent = "imtod";
                 nextComponent = "jtf";
             }
             
             if( runJointTourFrequencyModel ){
                 logJointModelResults( householdDataManager );
                 lastComponent = "jtf";
                 nextComponent = "jtl";
             }             
             
             if( runJointTourLocationChoiceModel ){
                 lastComponent = "jtl";
                 nextComponent = "jtod";
             }             
             
             if( runJointTourDepartureTimeAndDurationModel || runJointTourModeChoiceModel ){
                 lastComponent = "jtod";
                 nextComponent = "inmtf";
             }             
             
             if( runIndivNonManTourFrequencyModel ){
                 lastComponent = "inmtf";
                 nextComponent = "inmtl";
             }             
             
             if( runIndivNonManTourLocationChoiceModel ){
                 lastComponent = "inmtl";
                 nextComponent = "inmtod";
             }             
             
             if( runIndivNonManTourDepartureTimeAndDurationModel || runIndivNonManTourModeChoiceModel ){
                 lastComponent = "inmtod";
                 nextComponent = "awf";
             }             
             
             if( runAtWorkSubTourFrequencyModel ){
                 logAtWorkSubtourFreqResults( householdDataManager );
                 lastComponent = "awf";
                 nextComponent = "awl";
             }
             
             if( runAtWorkSubtourLocationChoiceModel ){
                 lastComponent = "awl";
                 nextComponent = "awtod";
             }
             
             if( runAtWorkSubtourDepartureTimeAndDurationModel || runAtWorkSubtourModeChoiceModel ){
                 lastComponent = "awtod";
                 nextComponent = "stf";
             }
             
             if( runStopFrequencyModel ){
                 logStfResults( householdDataManager, true);  //individual
                 logStfResults( householdDataManager, false ); //joint
                 lastComponent = "stf";
                 nextComponent = "stl";
             }             
             
             if( runStopLocationModel ){
                 lastComponent = "stl";
                 nextComponent = "done";
             }             
             
             
             
             // write a disk object fle for the householdDataManager, in case we want to restart from the next step.
             if ( hhDiskObjectFile != null && ! lastComponent.equalsIgnoreCase("uwsl") ) {
                 /*
                 logger.info ( String.format("writing household disk object file after %s choice model; may take a long time ...", lastComponent) );
                 String hhFileName = hhDiskObjectFile + "_" + nextComponent;
                 householdDataManager.createSerializedHhArrayInFileFromObject( hhFileName, nextComponent );
                 logger.info ( String.format("finished writing household disk object file = %s.", hhFileName) );
                 */
             }
             
             logger.info ( "finished with HouseholdChoiceModelRunner." );         

         }
         

         
         
         
         boolean writeTextFileFlag = false;
         boolean writeSqliteFlag = false;
         try {
             writeTextFileFlag = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_WRITE_DATA_TO_FILE);
         }
         catch ( MissingResourceException e ) {
             // if exception is caught while getting property file value, then boolean flag remains false
         }
         try {
             writeSqliteFlag = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_WRITE_DATA_TO_DATABASE);
         }
         catch ( MissingResourceException e ) {
             // if exception is caught while getting property file value, then boolean flag remains false
         }

         HouseholdDataWriter dataWriter = null;
         if ( writeTextFileFlag || writeSqliteFlag ) {
             dataWriter = new HouseholdDataWriter( resourceBundle, modelStructure, tazDataManager, dmuFactory, iteration );

             if ( writeTextFileFlag )
                dataWriter.writeDataToFiles(householdDataManager);
             
             if ( writeSqliteFlag ) {
                 String dbFilename = "";
                 try {
                     String baseDir = resourceBundle.getString(PROPERTIES_PROJECT_DIRECTORY);
                     dbFilename = baseDir + resourceBundle.getString(SQLITE_DATABASE_FILENAME) + "_" + iteration;
                     dataWriter.writeDataToDatabase(householdDataManager, dbFilename);
                 }
                 catch ( MissingResourceException e ) {
                     // if exception is caught while getting property file value, then boolean flag remains false
                 }
             }
         }
         
     }
     
     
     
     
     public String getProjectDirectoryName() {
        return projectDirectory;
     }


     private MatrixDataServer startMatrixServerProcess( String serverAddress, int serverPort ) {

         String className = MatrixDataServer.MATRIX_DATA_SERVER_NAME;

         MatrixDataServer matrixServer = new MatrixDataServer();

/*         try {

             // create the concrete data server object
             matrixServer.start32BitMatrixIoServer( MatrixType.TPPLUS );

         }
         catch ( RuntimeException e ) {
             matrixServer.stop32BitMatrixIoServer();
             logger.error ( "RuntimeException caught in com.pb.models.ctramp.MatrixDataServer.main() -- exiting.", e );
         }
*/
         // bind this concrete object with the cajo library objects for managing RMI
         try {
             Remote.config( serverAddress, serverPort, null, 0 );
         }
         catch ( UnknownHostException e ) {
             logger.error ( String.format( "UnknownHostException. serverAddress = %s, serverPort = %d -- exiting.", serverAddress, serverPort ), e );
             //matrixServer.stop32BitMatrixIoServer();
             throw new RuntimeException();
         }

         try {
             ItemServer.bind( matrixServer, className );
         }
         catch ( RemoteException e ) {
             logger.error ( String.format( "RemoteException. serverAddress = %s, serverPort = %d -- exiting.", serverAddress, serverPort ), e );
             //matrixServer.stop32BitMatrixIoServer();
             throw new RuntimeException();
         }

         return matrixServer;

     }

     
     public boolean restartFromDiskObjectFile() {
         return restartFromDiskObjectFile;
     }


     public void restartModels ( HouseholdDataManagerIf householdDataManager ) {

         // if no filename was specified for the previous shadow price info, restartIter == -1, and random counts will be reset to 0.
         boolean runUsualWorkSchoolChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_WORKSCHOOL_CHOICE);
         if ( runUsualWorkSchoolChoiceModel ) {
             householdDataManager.resetUwslRandom();
         }
         else {
        	 boolean runAutoOwnershipModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AUTO_OWNERSHIP);
        	 if ( runAutoOwnershipModel ) {
        		 householdDataManager.resetAoRandom();
        	 }
        	 else {
        		 boolean runFreeParkingAvailableModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_FREE_PARKING_AVAILABLE);
        		 if ( runFreeParkingAvailableModel  ) {
        			 householdDataManager.resetFpRandom();
        		 }
        		 else {
        			 boolean runCoordinatedDailyActivityPatternModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_DAILY_ACTIVITY_PATTERN);
        			 if ( runCoordinatedDailyActivityPatternModel  ) {
        				 householdDataManager.resetCdapRandom();
        			 }
        			 else {
        				 boolean runIndividualMandatoryTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_MANDATORY_TOUR_FREQ);
        				 if ( runIndividualMandatoryTourFrequencyModel  ) {
        					 householdDataManager.resetImtfRandom();
        				 }
        				 else {
        					 boolean runIndividualMandatoryTourDepartureAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_MAND_TOUR_DEP_TIME_AND_DUR);
        					 if ( runIndividualMandatoryTourDepartureAndDurationModel  ) {
        						 householdDataManager.resetImtodRandom();
        					 }

        					 else {
        						 boolean runIndividualMandatoryTourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_MAND_TOUR_MODE_CHOICE);
        						 if ( runIndividualMandatoryTourModeChoiceModel  ) {
        							 householdDataManager.resetImmcRandom();
        						 }
        						 else {
        							 boolean runJointTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_FREQ);
        							 if ( runJointTourFrequencyModel  ) {
        								 householdDataManager.resetJtfRandom();
        							 }
        							 else {
        								 boolean runJointTourLocationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_LOCATION_CHOICE);
        								 if ( runJointTourLocationModel  ) {
        									 householdDataManager.resetJtlRandom();
        								 }
        								 else {
        									 boolean runJointTourDepartureAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_DEP_TIME_AND_DUR);
        									 if ( runJointTourDepartureAndDurationModel  ) {
        										 householdDataManager.resetJtodRandom();
        									 }
        									 else {
        										 boolean runJointTourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_JOINT_TOUR_MODE_CHOICE);
        										 if ( runJointTourModeChoiceModel  ) {
        											 householdDataManager.resetJmcRandom();
        										 }
        										 else {
        											 boolean runIndividualNonMandatoryTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_FREQ);
        											 if ( runIndividualNonMandatoryTourFrequencyModel  ) {
        												 householdDataManager.resetInmtfRandom();
        											 }
        											 else {
        												 boolean runIndividualNonMandatoryTourLocationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_LOCATION_CHOICE);
        												 if ( runIndividualNonMandatoryTourLocationModel  ) {
        													 householdDataManager.resetInmtlRandom();
        												 }
        												 else {
        													 boolean runIndividualNonMandatoryTourDepartureAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_DEP_TIME_AND_DUR);
        													 if ( runIndividualNonMandatoryTourDepartureAndDurationModel  ) {
        														 householdDataManager.resetInmtodRandom();
        													 }

        													 else {
        														 boolean runIndividualNonMandatoryModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_INDIV_NON_MANDATORY_TOUR_MODE_CHOICE);
        														 if ( runIndividualNonMandatoryModeChoiceModel  ) {
        															 householdDataManager.resetInmmcRandom();
        														 }
        														 else {
        															 boolean runAtWorkSubTourFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_FREQ);
        															 if ( runAtWorkSubTourFrequencyModel  ) {
        																 householdDataManager.resetAwfRandom();
        															 }
        															 else {
        																 boolean runAtWorkSubtourLocationChoiceModel = ResourceUtil.getBooleanProperty( resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_LOCATION_CHOICE );
        																 if ( runAtWorkSubtourLocationChoiceModel  ) {
        																	 householdDataManager.resetAwlRandom();
        																 }
        																 else {
        																	 boolean runAtWorkSubtourDepartureTimeAndDurationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_DEP_TIME_AND_DUR);
        																	 if ( runAtWorkSubtourDepartureTimeAndDurationModel  ) {
        																		 householdDataManager.resetAwtodRandom();
        																	 }
        																	 else {
        																		 boolean runAtWorkSubtourModeChoiceModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_AT_WORK_SUBTOUR_MODE_CHOICE);
        																		 if ( runAtWorkSubtourModeChoiceModel  ) {
        																			 householdDataManager.resetAwmcRandom();
        																		 }
        																		 else {
        																			 boolean runStopFrequencyModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_STOP_FREQUENCY);
        																			 if ( runStopFrequencyModel  ) {
        																				 householdDataManager.resetStfRandom();
        																			 }
        																			 else {
        																				 boolean runStopLocationModel = ResourceUtil.getBooleanProperty(resourceBundle, PROPERTIES_RUN_STOP_LOCATION);
        																				 if ( runStopLocationModel  ) {
        																					 householdDataManager.resetStlRandom();
        																				 }
        																			 }
        																		 }
        																	 }
        																 }
        															 }
        														 }
        													 }
        												 }
        											 }
        										 }
        									 }
        								 }
        							 }
        						 }
        					 }
        				 }
        			 }
        		 }
        	 }
         }
     }



     /**
     private void createSerializedObjectInFileFromObject( Object objectToSerialize, String serializedObjectFileName, String serializedObjectKey ){
         try{
             DataFile dataFile = new DataFile( serializedObjectFileName, 1 );
             DataWriter dw = new DataWriter( serializedObjectKey );
             dw.writeObject( objectToSerialize );
             dataFile.insertRecord( dw );
             dataFile.close();
         }
         catch(NotSerializableException e) {
             logger.error( String.format("NotSerializableException for %s.  Trying to create serialized object with key=%s, in filename=%s.", objectToSerialize.getClass().getName(), serializedObjectKey, serializedObjectFileName ), e );
             throw new RuntimeException();
         }
         catch(IOException e) {
             logger.error( String.format("IOException trying to write disk object file=%s, with key=%s for writing.", serializedObjectFileName, serializedObjectKey ), e );
             throw new RuntimeException();
         }
     }


     private Object createObjectFromSerializedObjectInFile( Object newObject, String serializedObjectFileName, String serializedObjectKey ){
         try{
             DataFile dataFile = new DataFile( serializedObjectFileName, "r" );
             DataReader dr = dataFile.readRecord( serializedObjectKey );
             newObject = dr.readObject();
             dataFile.close();
             return newObject;
         }
         catch(IOException e) {
             logger.error( String.format("IOException trying to read disk object file=%s, with key=%s.", serializedObjectFileName, serializedObjectKey ), e );
             throw new RuntimeException();
         }
         catch(ClassNotFoundException e) {
             logger.error( String.format("could not instantiate %s object, with key=%s from filename=%s.", newObject.getClass().getName(), serializedObjectFileName, serializedObjectKey ), e );
             throw new RuntimeException();
         }
     }
     **/
     /**
      * Loops through the households in the HouseholdDataManager, gets the auto ownership
      * result for each household, and writes a text file with hhid and auto ownership.
      *
      * @param householdDataManager is the object from which the array of household objects can be retrieved.
      * @param projectDirectory is the root directory for the output file named
      */
     private void saveAoResults(HouseholdDataManagerIf householdDataManager, String projectDirectory){

         String aoResultsFileName;
         try {
             aoResultsFileName = resourceBundle.getString( PROPERTIES_RESULTS_AUTO_OWNERSHIP );
         }
         catch ( MissingResourceException e ) {
             // if filename not specified in properties file, don't need to write it.
             return;
         }

         
         FileWriter writer;
         PrintWriter outStream = null;
         if ( aoResultsFileName != null ) {

             aoResultsFileName = projectDirectory + aoResultsFileName;

             try {
                 writer = new FileWriter(new File(aoResultsFileName));
                 outStream = new PrintWriter (new BufferedWriter( writer ) );
             }
             catch(IOException e){
                 logger.fatal( String.format( "Exception occurred opening AO results file: %s.", aoResultsFileName ) );
                 throw new RuntimeException(e);
             }


             outStream.println ( "HHID,AO" );
             
             
             ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

             for ( int[] startEndIndices : startEndTaskIndicesList ) {
             
                 int startIndex = startEndIndices[0];
                 int endIndex = startEndIndices[1];

             
                 // get the array of households
                 Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
        
                 for(int i=0; i < householdArray.length; ++i){
        
                     Household household = householdArray[i];
                     int hhid = household.getHhId();
                     int ao = household.getAutoOwnershipModelResult();
        
                     outStream.println( String.format( "%d,%d", hhid, ao ) );
        
                 }
        
             }
             
             outStream.close();

         }

     }


     private void logAoResults( HouseholdDataManagerIf householdDataManager ){
         
         String[] aoCategoryLabel = { "0 autos", "1 auto", "2 autos", "3 autos", "4 or more autos" };
         
         logger.info( "" );
         logger.info( "" );
         logger.info( "Auto Ownership Model Results" );
         logger.info( String.format("%-16s  %10s", "Category", "Num HHs" ));
         
         
         
         // track the results
         int[] hhsByAutoOwnership;
         hhsByAutoOwnership = new int[aoCategoryLabel.length];


         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];

         
             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for(int i=0; i < householdArray.length; ++i){
    
                 Household household = householdArray[i];
                 int ao = household.getAutoOwnershipModelResult();
                 
                 if ( ao >= aoCategoryLabel.length )
                     ao = aoCategoryLabel.length - 1;
                 
                 hhsByAutoOwnership[ao]++;
    
             }
    
    
         }
         
         
         int total = 0;
         for (int i=0; i < hhsByAutoOwnership.length; i++) {
             logger.info( String.format("%-16s  %10d", aoCategoryLabel[i], hhsByAutoOwnership[i] ));
             total += hhsByAutoOwnership[i];
         }
         logger.info( String.format("%-16s  %10d", "Total", total ));
         
     }


     private void logFpResults( HouseholdDataManagerIf householdDataManager ){
         
         String[] fpCategoryLabel = { "Free Available", "Must Pay" };
         
         logger.info( "" );
         logger.info( "" );
         logger.info( "Free Parking Model Results" );
         logger.info( String.format("%-16s  %10s", "Category", "Num Persons" ));
         
         
         
         // track the results
         int[] personsByFreeParking;
         personsByFreeParking = new int[fpCategoryLabel.length];


         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];

         
             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for(int i=0; i < householdArray.length; ++i){
    
            	 // note that person is a 1-based array
                 Household household = householdArray[i];
                 Person[] person = household.getPersons(); 
                 for (int j=1; j<person.length; j++) {
                     int fp = person[j].getFreeParkingAvailableResult() - 1;
                     
                     if ( fp >= 0 ) {
                         personsByFreeParking[fp]++;
                     }
                     else {
                         logger.error( "invalid result = " + fp + " for personid = " + person[j].getPersonId() );
                         throw new RuntimeException();
                     }
        
                 }
             }
    
    
         }
         
         
         int total = 0;
         for (int i=0; i < personsByFreeParking.length; i++) {
             logger.info( String.format("%-16s  %10d", fpCategoryLabel[i], personsByFreeParking[i] ));
             total += personsByFreeParking[i];
         }
         logger.info( String.format("%-16s  %10d", "Total", total ));
         
     }


     /**
      * Records the coordinated daily activity pattern model results to the logger. A household-level
      * summary simply records each pattern type and a person-level summary summarizes the activity
      * choice by person type (full-time worker, university student, etc). 
      *
      */
     public void logCdapResults( HouseholdDataManagerIf householdDataManager ){                

         String[] activityNameArray = { Definitions.MANDATORY_PATTERN, Definitions.NONMANDATORY_PATTERN, Definitions.HOME_PATTERN };

         getLogReportSummaries( householdDataManager );
         
         
         
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info("Coordinated Daily Activity Pattern Model Results");
         
         // count of activities by person type
         logger.info(" ");
         logger.info("CDAP Results: Count of activities by person type");
         String firstHeader  = "Person type                    ";
         String secondHeader = "-----------------------------  ";
         for(int i=0;i<activityNameArray.length;++i){
             firstHeader  += "        " + activityNameArray[i] + " ";
             secondHeader += "--------- ";
         }

         firstHeader  += "    Total";
         secondHeader += "---------";

         logger.info(firstHeader);
         logger.info(secondHeader);

         int[] columnTotals = new int[activityNameArray.length];

         for(int i=0;i<Person.personTypeNameArray.length;++i){
             String personType = Person.personTypeNameArray[i];
             String stringToLog  = String.format("%-30s", personType);
             int lineTotal = 0;

             if ( cdapByPersonTypeAndActivity.containsKey(personType) ) {
                 
                 for(int j=0;j<activityNameArray.length;++j){
                     int count = 0;
                     if( cdapByPersonTypeAndActivity.get(personType).containsKey(activityNameArray[j]) ) {
                         count = cdapByPersonTypeAndActivity.get(personType).get(activityNameArray[j]);
                     }
                     stringToLog += String.format("%10d",count);

                     lineTotal += count;
                     columnTotals[j] += count;
                 } // j
                 
             } // if key
             
             stringToLog += String.format("%10d",lineTotal);
             logger.info(stringToLog);
             
         } // i

         logger.info(secondHeader);

         String stringToLog  = String.format("%-30s", "Total");
         int lineTotal = 0;
         for(int j=0;j<activityNameArray.length;++j){
             stringToLog += String.format("%10d",columnTotals[j]);
             lineTotal += columnTotals[j];
         } // j

         stringToLog += String.format("%10d",lineTotal);
         logger.info(stringToLog);


         // count of patterns
         logger.info(" ");
         logger.info(" ");
         logger.info("CDAP Results: Count of patterns");
         logger.info("Pattern                Count");
         logger.info("------------------ ---------");
         
         // sort the map by hh size first
         Set<Integer> hhSizeKeySet = cdapByHhSizeAndPattern.keySet();
         Integer[] hhSizeKeyArray = new Integer[hhSizeKeySet.size()];
         hhSizeKeySet.toArray(hhSizeKeyArray);
         Arrays.sort(hhSizeKeyArray);

         int total = 0;
         for(int i=0;i<hhSizeKeyArray.length;++i){
             
             // sort the patterns alphabetically
             HashMap<String,Integer> patternMap = cdapByHhSizeAndPattern.get(hhSizeKeyArray[i]);
             Set<String> patternKeySet = patternMap.keySet();
             String[] patternKeyArray = new String[patternKeySet.size()];
             patternKeySet.toArray(patternKeyArray);
             Arrays.sort(patternKeyArray);
             for(int j=0;j<patternKeyArray.length;++j){
                 int count = patternMap.get(patternKeyArray[j]);
                 total += count;
                 logger.info(String.format("%-18s%10d",patternKeyArray[j],count));
             }
             
         }
         
         logger.info("------------------ ---------");
         logger.info(String.format("%-18s%10d","Total",total));
         logger.info(" ");

         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info(" ");
         logger.info(" ");
         
     }

     
     /**
      * Logs the results of the individual mandatory tour frequency model.
      *
      */
     public void logImtfResults( HouseholdDataManagerIf householdDataManager ){
         
         String[] choiceResults = {"1 Work", "2 Work", "1 School", "2 School", "Wrk & Schl"};
         
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info("Individual Mandatory Tour Frequency Model Results");
         
         // count of model results
         logger.info(" ");
         String firstHeader  = "Person type                   ";
         String secondHeader = "-----------------------------  ";
         
         
         // summarize results
         HashMap<String,int[]> countByPersonType = new HashMap<String,int[]>();
         
         
         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];

         
             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for(int i=0; i < householdArray.length; ++i){
    
                 Person[] personArray = householdArray[i].getPersons();
                 for(int j=1; j < personArray.length; j++){
                 
                     // only summarize persons with mandatory pattern
                     String personActivity = personArray[j].getCdapActivity();
                     if ( personActivity != null && personArray[j].getCdapActivity().equalsIgnoreCase("M") ) {
                     
                         String personTypeString = personArray[j].getPersonType();
                         int choice = personArray[j].getImtfChoice();
                         
                         // count the results
                         if(countByPersonType.containsKey(personTypeString)){
    
                             int[] counterArray = countByPersonType.get(personTypeString);
                             counterArray[choice-1]++;
                             countByPersonType.put(personTypeString, counterArray);
    
                         }
                         else{
    
                             int[] counterArray = new int[choiceResults.length];
                             counterArray[choice-1]++;
                             countByPersonType.put(personTypeString, counterArray);
    
                         }
                     }
                     
                 }    
    
             }
    
    
         }
         
         
         
         for(int i=0;i<choiceResults.length;++i){
             firstHeader  += String.format("%12s",choiceResults[i]);
             secondHeader += "----------- ";
         }
         
         firstHeader  += String.format("%12s","Total");
         secondHeader += "-----------";

         logger.info(firstHeader);
         logger.info(secondHeader);

         int[] columnTotals = new int[choiceResults.length];


         int lineTotal = 0;
         for(int i=0;i<Person.personTypeNameArray.length;++i){
             String personTypeString = Person.personTypeNameArray[i];
             String stringToLog  = String.format("%-30s", personTypeString);

             if(countByPersonType.containsKey(personTypeString)){
                 
                 lineTotal = 0;
                 int[] countArray = countByPersonType.get(personTypeString);
                 for(int j=0;j<choiceResults.length;++j){
                     stringToLog += String.format("%12d",countArray[j]);
                     columnTotals[j] += countArray[j];
                     lineTotal += countArray[j];
                 } // j
             } // if key
             else{
                 
                 // log zeros
                 lineTotal = 0;
                 for(int j=0;j<choiceResults.length;++j){
                     stringToLog += String.format("%12d",0);
                 }
             }

             stringToLog += String.format("%12d",lineTotal);

             logger.info(stringToLog);
             
         } // i
         
         String stringToLog  = String.format("%-30s", "Total");
         lineTotal = 0;
         for(int j=0;j<choiceResults.length;++j){
             stringToLog += String.format("%12d",columnTotals[j]);
             lineTotal += columnTotals[j];
         } // j

         logger.info(secondHeader);
         stringToLog += String.format("%12d",lineTotal);
         logger.info(stringToLog);
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info(" ");
         logger.info(" ");

     }

     
     private void logJointModelResults( HouseholdDataManagerIf householdDataManager ) {

         String[] altLabels = modelStructure.getJtfAltLabels();

         // this is the first index in the summary array for choices made by eligible households
         int indexOffset = 4;
         int[] jointTourChoiceFreq = new int[altLabels.length + indexOffset + 1];
     
         
         TreeMap<String, Integer> partySizeFreq = new TreeMap<String, Integer>();

         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];


             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for(int i=0; i < householdArray.length; ++i){

                 Tour[] jt = householdArray[i].getJointTourArray();
                 int jtfAlt = householdArray[i].getJointTourFreqChosenAlt();
                 
                 if ( jt == null ) {

                     int index = 0;
                     if ( jtfAlt > 1 ) {
                         logger.error ( String.format( "HHID=%d, joint tour array is null, but a valid alternative=%d is recorded for the household.", householdArray[i].getHhId(), jtfAlt ) );
                         throw new RuntimeException();
                     }
                     else if ( jtfAlt == 1 ) {
                         index = indexOffset + jtfAlt;
                     }
                     else {
                         index = -jtfAlt; 
                     }

                     jointTourChoiceFreq[index]++;
                 }
                 else {
                     

                     if ( jtfAlt <= 1 ) {
                         logger.error ( String.format( "HHID=%d, joint tour array is not null, but an invalid alternative=%d is recorded for the household.", householdArray[i].getHhId(), jtfAlt ) );
                         throw new RuntimeException();
                     }

                     int index = indexOffset + jtfAlt;
                     jointTourChoiceFreq[index]++;


                     // determine party size frequency for joint tours generated
                     Person[] persons = householdArray[i].getPersons();
                     for ( int j=0; j < jt.length; j++ ) {

                         int compAlt = jt[j].getJointTourComposition();

                         // determine number of children and adults in tour
                         int adults = 0;
                         int children = 0;
                         byte[] participants = jt[j].getPersonNumArray();
                         for ( int k=0; k < participants.length; k++ ) {
                             index = participants[k];
                             Person person = persons[index];
                             if ( person.getPersonIsAdult() == 1 )
                                 adults++;
                             else
                                 children++;
                         }
                         
                         // create a key to use for a frequency map for "JointTourPurpose_Composition_NumAdults_NumChildren"
                         String key = String.format( "%s_%d_%d_%d", jt[j].getTourPurpose(), compAlt, adults, children );

                         int value = 0;
                         if ( partySizeFreq.containsKey( key ) )
                             value = partySizeFreq.get( key );
                         partySizeFreq.put( key, ++value );

                     }
                     
                 }

             }
    
         }

         
         
         
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info("Joint Tour Frequency and Joint Tour Composition Model Results");


         logger.info(" ");
         logger.info( "Frequency Table of Households by Joint Tour Frequency Choice" );
         logger.info ( String.format( "%-5s   %-26s   %12s", "Alt", "Alt Name", "Households" ) );


         // treat the first few rows of the table differently - no joint tours chosen and therefore no composition chosen.
         // so we just want to print the total households chhosing no joint tours in the "total" column.
         // later we'll add this total to the tanle total in the last row.
         logger.info ( String.format( "%-5s   %-26s   %12d", "-", "single person hh", jointTourChoiceFreq[2] ) );
         logger.info ( String.format( "%-5s   %-26s   %12d", "-", "less than 2 persons travel", jointTourChoiceFreq[3] ) );
         logger.info ( String.format( "%-5s   %-26s   %12d", "-", "only pre-schoolers travel", jointTourChoiceFreq[4] ) );


         int rowTotal = jointTourChoiceFreq[2] + jointTourChoiceFreq[3] + jointTourChoiceFreq[4];
         for ( int i=1; i < altLabels.length; i++ ) {
             int index = indexOffset + i;
             logger.info ( String.format( "%-5d   %-26s   %12d", i, altLabels[i], jointTourChoiceFreq[index] ) );
             rowTotal += jointTourChoiceFreq[index];
         }
         logger.info ( String.format( "%-34s   %12d", "Total Households", rowTotal ) );


         logger.info(" ");
         logger.info(" ");
         logger.info(" ");

         logger.info( "Frequency Table of Joint Tours by All Parties Generated" );
         logger.info ( String.format( "%-5s   %-10s   %-10s   %10s   %10s   %10s", "N", "Purpose", "Type", "Adults", "Children", "Freq" ) );


         int count = 1;
         for ( String key : partySizeFreq.keySet() ) {

             int start = 0;
             int end = 0;
             int compIndex = 0;
             int adults = 0;
             int children = 0;
             String indexString = "";
             String purpose = "";

             start = 0;
             end = key.indexOf( '_', start );
             purpose = key.substring( start, end );

             start = end+1;
             end = key.indexOf( '_', start );
             indexString = key.substring( start, end );
             compIndex = Integer.parseInt ( indexString );

             start = end+1;
             end = key.indexOf( '_', start );
             indexString = key.substring( start, end );
             adults = Integer.parseInt ( indexString );

             start = end+1;
             indexString = key.substring( start );
             children = Integer.parseInt ( indexString );

             logger.info ( String.format( "%-5d   %-10s   %-10s   %10d   %10d   %10d", count++, purpose, JointTourModels.JOINT_TOUR_COMPOSITION_NAMES[compIndex], adults, children, partySizeFreq.get(key) ) );
         }


         logger.info(" ");
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info(" ");

     }

     /**
      * Logs the results of the individual/joint tour stop frequency model.
      *
      */
     public void logStfResults( HouseholdDataManagerIf householdDataManager, Boolean isIndividual){
         
    	 logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info((isIndividual ? "Individual" : "Joint") + " Tour Stop Frequency Model Results");
         
         // count of model results
         logger.info(" ");
         String firstHeader  = "Tour Purpose     ";
         String secondHeader = "---------------   ";
         
         int[] obStopsAlt = StopFrequencyDMU.NUM_OB_STOPS_FOR_ALT;
         int[] ibStopsAlt = StopFrequencyDMU.NUM_IB_STOPS_FOR_ALT;
         
         // 17 purposes
         int[][] chosen = new int[obStopsAlt.length][18];
         HashMap<Integer, String> indexPurposeMap = modelStructure.getSegmentedIndexPurposeMap();
         HashMap<String, Integer> purposeIndexMap = modelStructure.getSegmentedPurposeIndexMap();
         indexPurposeMap.put( 1, "work_low" );
         purposeIndexMap.put( "work_low", 1 );
         indexPurposeMap.put( 2, "work_med" );
         purposeIndexMap.put( "work_med", 2 );
         indexPurposeMap.put( 3, "work_high" );
         purposeIndexMap.put( "work_high", 3 );
         indexPurposeMap.put( 4, "work_very high" );
         purposeIndexMap.put( "work_very high", 4 );
         indexPurposeMap.put( 5, "university" );
         purposeIndexMap.put( "university", 5 );
         indexPurposeMap.put( 6, "school_drive" );
         purposeIndexMap.put( "school_drive", 6 );
         indexPurposeMap.put( 7, "school_predrive" );
         purposeIndexMap.put( "school_predrive", 7 );
         indexPurposeMap.put( 8, "escort_kids" );
         purposeIndexMap.put( "escort_kids", 8 );
         indexPurposeMap.put( 9, "escort_no kids" );
         purposeIndexMap.put( "escort_no kids", 9 );
         indexPurposeMap.put( 10, "shopping" );
         purposeIndexMap.put( "shopping", 10 );
         indexPurposeMap.put( 11, "othmaint" );
         purposeIndexMap.put( "othmaint", 11 );
         indexPurposeMap.put( 12, "eatout" );
         purposeIndexMap.put( "eatout", 12 );
         indexPurposeMap.put( 13, "othdiscr" );
         purposeIndexMap.put( "othdiscr", 13 );
         indexPurposeMap.put( 14, "social" );
         purposeIndexMap.put( "social", 14 );
         indexPurposeMap.put( 15, "atwork_business" );
         purposeIndexMap.put( "atwork_business", 15 );
         indexPurposeMap.put( 16, "atwork_eat" );
         purposeIndexMap.put( "atwork_eat", 16 );
         indexPurposeMap.put( 17, "atwork_maint" );
         purposeIndexMap.put( "atwork_maint", 17 );
         
         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];

         
             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for(int i=0; i < householdArray.length; ++i){
    
            	 if(isIndividual) {
            	 
            		 //individual tours
	                 Person[] personArray = householdArray[i].getPersons();
	                 for(int j=1; j < personArray.length; j++){
	                 
	                     List<Tour> tourList = new ArrayList<Tour>();
	                     
	                     // apply stop frequency for all person tours
	                     tourList.addAll( personArray[j].getListOfWorkTours() );
	                     tourList.addAll( personArray[j].getListOfSchoolTours() );
	                     tourList.addAll( personArray[j].getListOfIndividualNonMandatoryTours() );
	                     tourList.addAll( personArray[j].getListOfAtWorkSubtours() );
	
	                     for ( Tour t : tourList ) {
	                         
	                         int index = purposeIndexMap.get( t.getTourPurpose().toLowerCase() );
	                         int choice = t.getStopFreqChoice();
	                         chosen[choice][index]++;
	                         
	                     }
	                     
	                 } 
	                 
            	 } else {
	                	
            		 //joint tours
            		 Tour[] jointTourArray = householdArray[i].getJointTourArray(); 
            		 if (jointTourArray!=null) {
	                     for (int j=0; j<jointTourArray.length; j++) {
	                         
	                         int index = purposeIndexMap.get( jointTourArray[j].getTourPurpose().toLowerCase() );
	                         int choice = jointTourArray[j].getStopFreqChoice();
	                         chosen[choice][index]++;
	                         
	                     }
            		 }
            		 
	             }
    
             }
    
    
         }
         
         
         
         for(int i=1;i<chosen[1].length;++i){
             firstHeader  += String.format("%18s", indexPurposeMap.get(i) );
             secondHeader += "  --------------- ";
         }
         
         firstHeader  += String.format("%18s","Total");
         secondHeader += "  --------------- ";

         logger.info(firstHeader);
         logger.info(secondHeader);

         int[] columnTotals = new int[chosen[1].length];


         int lineTotal = 0;
         for(int i=1;i<chosen.length;++i){
             String stringToLog  = String.format("%d out, %d in      ", obStopsAlt[i], ibStopsAlt[i] );

             lineTotal = 0;
             int[] countArray = chosen[i];
             for(int j=1;j<countArray.length;++j){
                 stringToLog += String.format("%18d",countArray[j]);
                 columnTotals[j] += countArray[j];
                 lineTotal += countArray[j];
             } // j

             stringToLog += String.format("%18d",lineTotal);

             logger.info(stringToLog);
             
         } // i
         
         String stringToLog  = String.format("%-17s", "Total");
         lineTotal = 0;
         for(int j=1;j<chosen[1].length;++j){
             stringToLog += String.format("%18d",columnTotals[j]);
             lineTotal += columnTotals[j];
         } // j

         logger.info(secondHeader);
         stringToLog += String.format("%18d",lineTotal);
         logger.info(stringToLog);
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info(" ");
         logger.info(" ");

     }
     

     

     
     private void getLogReportSummaries( HouseholdDataManagerIf householdDataManager ) {

         
         // summary collections
         cdapByHhSizeAndPattern = new HashMap<Integer,HashMap<String,Integer>>();
         cdapByPersonTypeAndActivity = new HashMap<String,HashMap<String,Integer>>();

         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];
         
             // get the array of households
             Household[] partialHhArray = householdDataManager.getHhArray( startIndex, endIndex );
    
             for ( Household hhObject : partialHhArray ) {
                 
                 // get the household's activity pattern choice
                 String pattern = hhObject.getCoordinatedDailyActivityPattern();

                 Person[] personArray = hhObject.getPersons();
                 for( int j=1; j < personArray.length; j++ ) {
                     
                     // get person's activity string 
                     String activityString = personArray[j].getCdapActivity();

                     // get the person type to simmarize results by
                     String personTypeString = personArray[j].getPersonType();

                     // check if the person type is in the map
                     if( cdapByPersonTypeAndActivity.containsKey( personTypeString )){

                         HashMap<String,Integer> activityCountMap = cdapByPersonTypeAndActivity.get( personTypeString );

                         // check if the activity is in the activity map
                         int currentCount = 1;
                         if( activityCountMap.containsKey(activityString) )
                             currentCount = activityCountMap.get(activityString) + 1;
                             
                         activityCountMap.put(activityString, currentCount);
                         cdapByPersonTypeAndActivity.put(personTypeString, activityCountMap);


                     }
                     else{
                         
                         HashMap<String,Integer> activityCountMap = new HashMap<String,Integer>();
                         activityCountMap.put(activityString, 1);
                         cdapByPersonTypeAndActivity.put(personTypeString, activityCountMap);

                     } // is personType in map if

                 } // j (person loop)


                 // count each type of pattern string by hhSize
                 if( cdapByHhSizeAndPattern.containsKey(pattern.length()) ) {
                     
                     HashMap<String,Integer> patternCountMap = cdapByHhSizeAndPattern.get( pattern.length() );
                     
                     int currentCount = 1;
                     if( patternCountMap.containsKey(pattern) )
                         currentCount = patternCountMap.get(pattern) + 1;
                     patternCountMap.put(pattern, currentCount);
                     cdapByHhSizeAndPattern.put(pattern.length(), patternCountMap);
                     
                 }
                 else {
                     
                     HashMap<String,Integer> patternCountMap = new HashMap<String,Integer>();
                     patternCountMap.put(pattern, 1);
                     cdapByHhSizeAndPattern.put(pattern.length(), patternCountMap);

                 } // is personType in map if

             }
    
         }

     }
     
         
     /**
      * Loops through the households in the HouseholdDataManager, gets the coordinated daily
      * activity pattern for each person in the household, and writes a text file with hhid,
      * personid, persnum, and activity pattern.
      *
      * @param householdDataManager
      */
     public void saveCdapResults( HouseholdDataManagerIf householdDataManager, String projectDirectory ){

         String cdapResultsFileName;
         try {
             cdapResultsFileName = resourceBundle.getString( PROPERTIES_RESULTS_CDAP );
         }
         catch ( MissingResourceException e ) {
             // if filename not specified in properties file, don't need to write it.
             return;
         }
         
         
         FileWriter writer;
         PrintWriter outStream = null;
         if ( cdapResultsFileName != null ) {

             cdapResultsFileName = projectDirectory + cdapResultsFileName;

             try {
                 writer = new FileWriter(new File(cdapResultsFileName));
                 outStream = new PrintWriter (new BufferedWriter( writer ) );
             }
             catch(IOException e){
                 logger.fatal( String.format( "Exception occurred opening CDAP results file: %s.", cdapResultsFileName ) );
                 throw new RuntimeException(e);
             }


             outStream.println( "HHID,PersonID,PersonNum,PersonType,ActivityString" );


             ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

             for ( int[] startEndIndices : startEndTaskIndicesList ) {
             
                 int startIndex = startEndIndices[0];
                 int endIndex = startEndIndices[1];

             
                 // get the array of households
                 Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
        
                 for(int i=0; i < householdArray.length; ++i){

                     Household household = householdArray[i];
                     int hhid = household.getHhId();
    
                     // get the pattern for each person
                     Person[] personArray = household.getPersons();
                     for( int j=1; j < personArray.length; j++ ) {
    
                         Person person = personArray[j];
                         
                         int persId = person.getPersonId();
                         int persNum = person.getPersonNum();
                         int persType = person.getPersonTypeNumber();
                         String activityString = person.getCdapActivity();
    
                         outStream.println( String.format("%d,%d,%d,%d,%s", hhid, persId, persNum, persType, activityString ));

                     } // j (person loop)
                     
                 }

             }

             outStream.close();

         }

     }



     
     /**
      * Logs the results of the model.
      *
      */
     public void logAtWorkSubtourFreqResults( HouseholdDataManagerIf householdDataManager ){
         
         String[] alternativeNames = modelStructure.getAwfAltLabels();
         HashMap<String,int[]> awfByPersonType = new HashMap<String,int[]>();
         
         ArrayList<int[]> startEndTaskIndicesList = getWriteHouseholdRanges( householdDataManager.getNumHouseholds() );

         for ( int[] startEndIndices : startEndTaskIndicesList ) {
         
             int startIndex = startEndIndices[0];
             int endIndex = startEndIndices[1];
         
             // get the array of households
             Household[] householdArray = householdDataManager.getHhArray( startIndex, endIndex );
             for(int i=0; i < householdArray.length; ++i){

                 // get this household's person array
                 Person[] personArray = householdArray[i].getPersons();
                 
                 // loop through the person array (1-based)
                 for(int j=1;j<personArray.length;++j){

                     Person person = personArray[j];
                     
                     // loop through the work tours for this person
                     ArrayList<Tour> tourList = person.getListOfWorkTours();
                     if ( tourList == null || tourList.size() == 0 )
                         continue;
                     
                     // count the results by person type
                     String personTypeString = person.getPersonType();


                     for ( Tour workTour : tourList ) {

                         int choice = 0;
                         if ( person.getListOfAtWorkSubtours().size() == 0 )
                             choice = 1;
                         else {
                             choice = workTour.getSubtourFreqChoice();
                             if ( choice == 0 )
                                 choice++;
                         }
                         
                         
                         // count the results by person type
                         if( awfByPersonType.containsKey(personTypeString)){
                             int[] counterArray = awfByPersonType.get(personTypeString);
                             counterArray[choice-1]++;
                             awfByPersonType.put( personTypeString, counterArray );

                         }
                         else{
                             int[] counterArray = new int[alternativeNames.length];
                             counterArray[choice-1]++;
                             awfByPersonType.put(personTypeString, counterArray);
                         }

                     }
                         

                 }

             }
    
         }

         
         
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info("At-Work Subtour Frequency Model Results");
         
         // count of model results
         logger.info(" ");
         String firstHeader  = "Person type                 ";
         String secondHeader = "---------------------------     ";
         
         
         for(int i=0;i<alternativeNames.length;++i){
             firstHeader  += String.format("%16s",alternativeNames[i]);
             secondHeader += "------------    ";
         }
         
         firstHeader  += String.format("%16s","Total");
         secondHeader += "------------";

         logger.info(firstHeader);
         logger.info(secondHeader);

         int[] columnTotals = new int[alternativeNames.length];


         int lineTotal = 0;
         for(int i=0;i<Person.personTypeNameArray.length;++i){
             String personTypeString = Person.personTypeNameArray[i];
             String stringToLog  = String.format("%-28s", personTypeString);

             if(awfByPersonType.containsKey(personTypeString)){
                 
                 lineTotal = 0;
                 int[] countArray = awfByPersonType.get(personTypeString);
                 for(int j=0;j<alternativeNames.length;++j){
                     stringToLog += String.format("%16d",countArray[j]);
                     columnTotals[j] += countArray[j];
                     lineTotal += countArray[j];
                 } // j

             } // if key
             else{
                 
                 // log zeros
                 lineTotal = 0;
                 for(int j=0;j<alternativeNames.length;++j){
                     stringToLog += String.format("%16d",0);
                 }
             }

             stringToLog += String.format("%16d",lineTotal);

             logger.info(stringToLog);
             
         } // i
         
         String stringToLog  = String.format("%-28s", "Total");
         lineTotal = 0;
         for(int j=0;j<alternativeNames.length;++j){
             stringToLog += String.format("%16d",columnTotals[j]);
             lineTotal += columnTotals[j];
         } // j

         logger.info(secondHeader);
         stringToLog += String.format("%16d",lineTotal);
         logger.info(stringToLog);
         logger.info(" ");
         logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
         logger.info(" ");
         logger.info(" ");

     }

     
     
     
     
     
     
     
     protected ArrayList<int[]> getWriteHouseholdRanges( int numberOfHouseholds ) {
         
         ArrayList<int[]> startEndIndexList = new ArrayList<int[]>(); 

         int startIndex = 0;
         int endIndex = 0;
         
         while ( endIndex < numberOfHouseholds - 1 ) {
             endIndex = startIndex + NUM_WRITE_PACKETS - 1;
             if ( endIndex + NUM_WRITE_PACKETS > numberOfHouseholds )
                 endIndex = numberOfHouseholds - 1;
         
             int[] startEndIndices = new int[2];
             startEndIndices[0] = startIndex; 
             startEndIndices[1] = endIndex;
             startEndIndexList.add( startEndIndices );
             
             startIndex += NUM_WRITE_PACKETS;
         }

         
         return startEndIndexList;
         
     }

}
