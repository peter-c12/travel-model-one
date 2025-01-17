package com.pb.models.ctramp.jppf;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.pb.common.calculator.IndexValues;
import com.pb.common.calculator.VariableTable;
import com.pb.common.newmodel.ChoiceModelApplication;
import com.pb.common.util.IndexSort;
import com.pb.models.ctramp.CtrampDmuFactoryIf;
import com.pb.models.ctramp.DcSoaDMU;
import com.pb.models.ctramp.DestChoiceDMU;
import com.pb.models.ctramp.DestChoiceSize;
import com.pb.models.ctramp.Household;
import com.pb.models.ctramp.Person;
import com.pb.models.ctramp.Tour;
import com.pb.models.ctramp.jppf.DestinationSampleOfAlternativesModel;
import com.pb.models.ctramp.ModeChoiceDMU;
import com.pb.models.ctramp.jppf.ModeChoiceModel;
import com.pb.models.ctramp.ModelStructure;
import com.pb.models.ctramp.TazDataIf;

import org.apache.log4j.Logger;

public class HouseholdIndNonManDestChoiceModel implements Serializable {

    private transient Logger logger = Logger.getLogger(HouseholdIndNonManDestChoiceModel.class);
    private transient Logger dcNonManLogger =  Logger.getLogger("tourDcNonMan");

    
    // TODO eventually remove this target
    private static final String PROPERTIES_UEC_DEST_CHOICE        = "UecFile.DestinationChoice";
    private static final String PROPERTIES_UEC_DEST_CHOICE_SOA    = "UecFile.SampleOfAlternativesChoice";

    private static final String PROPERTIES_NON_MAN_SOA_SAMPLE_SIZE_KEY = "IndividualNonMandatoryTourLocationChoice.SampleOfAlternatives.SampleSize";

    private static final int DC_DATA_SHEET = 0;

    

    private String tourCategory;
    private ModelStructure modelStructure;

    private String[] tourPurposeList;
    private HashMap<String, Integer> purposeModelIndexMap;

    private ModeChoiceDMU mcDmuObject;
    private DestChoiceDMU dcDmuObject;
    private DcSoaDMU dcSoaDmuObject;

    
    private ModeChoiceModel mcModel;
    private DestinationSampleOfAlternativesModel dcSoaModel;
    private ChoiceModelApplication dcModel[];

    private int numberOfSubzones;
    private int numberOfZones;

    private int modelIndex;


    //public HouseholdIndNonManDestChoiceModel( HashMap<String, String> propertyMap, ModelStructure modelStructure, TazDataIf tazDataManager, CtrampDmuFactoryIf dmuFactory, ModeChoiceModel mcModel, double[][][] indivNonManProbabilitiesCache, double[][][] indivNonManCumProbabilitiesCache ){
        public HouseholdIndNonManDestChoiceModel( HashMap<String, String> propertyMap, ModelStructure modelStructure, TazDataIf tazDataManager, CtrampDmuFactoryIf dmuFactory, ModeChoiceModel mcModel ){

        this.tourCategory = ModelStructure.INDIVIDUAL_NON_MANDATORY_CATEGORY;

        logger.info ( String.format( "creating %s tour dest choice model instance", tourCategory ) );
        
    	// set the model structure and the tour purpose list
    	this.modelStructure = modelStructure;
        this.mcModel = mcModel;

        // create an array of ChoiceModelApplication objects for each choice purpose
        //setupDestChoiceModelArrays( propertyMap, tazDataManager, dmuFactory, indivNonManProbabilitiesCache, indivNonManCumProbabilitiesCache );
        setupDestChoiceModelArrays( propertyMap, tazDataManager, dmuFactory );
    	
    }



        //private void setupDestChoiceModelArrays( HashMap<String, String> propertyMap, TazDataIf tazDataManager, CtrampDmuFactoryIf dmuFactory, double[][][] indivNonManProbabilitiesCache, double[][][] indivNonManCumProbabilitiesCache ) {
            private void setupDestChoiceModelArrays( HashMap<String, String> propertyMap, TazDataIf tazDataManager, CtrampDmuFactoryIf dmuFactory ) {
        
        String projectDirectory = propertyMap.get( CtrampApplication.PROPERTIES_PROJECT_DIRECTORY );

        String dcUecFileName = propertyMap.get( PROPERTIES_UEC_DEST_CHOICE );
        dcUecFileName = projectDirectory + dcUecFileName;

        String soaUecFileName = propertyMap.get( PROPERTIES_UEC_DEST_CHOICE_SOA );
        soaUecFileName = projectDirectory + soaUecFileName;

        int soaSampleSize = getSoaSampleSize( propertyMap );
        
        
        
        tourPurposeList = modelStructure.getDcModelPurposeList( this.tourCategory );


        numberOfSubzones = tazDataManager.getNumberOfSubZones();
        numberOfZones    = tazDataManager.getNumberOfZones();
        

        dcDmuObject = dmuFactory.getDestChoiceDMU();        
        dcSoaDmuObject = dmuFactory.getDcSoaDMU();
        mcDmuObject = dmuFactory.getModeChoiceDMU();
        

        
        // setup the object for calculating destination choice attraction size terms and managing shadow price calculations.
        DestChoiceSize dcSizeObj = new DestChoiceSize( modelStructure, tazDataManager );
        dcSizeObj.setupDestChoiceSize( propertyMap, projectDirectory, tourCategory );
        dcSizeObj.calculateDcSize();
 

        
        dcDmuObject.setDestChoiceSize(dcSizeObj);
        dcSoaDmuObject.setDestChoiceSizeObject(dcSizeObj);
        
        
        // create a sample of alternatives choice model object for use in selecting a sample
        // of all possible destination choice alternatives.
        dcSoaModel = new DestinationSampleOfAlternativesModel( soaUecFileName, soaSampleSize, propertyMap, modelStructure, tourCategory, tazDataManager, dcSizeObj, dcSoaDmuObject );
    
    
        // create a HashMap to map purposeName to model index
        purposeModelIndexMap = new HashMap<String, Integer>();

        // keep a set of unique model sheet numbers so that we can create ChoiceModelApplication objects once for each model sheet used
        TreeSet<Integer> modelIndexSet = new TreeSet<Integer>();
        
        int maxUecIndex = 0;
        for ( String purposeName : tourPurposeList ) {
            int uecIndex = modelStructure.getDcUecIndexForPurpose( purposeName );
            purposeModelIndexMap.put( purposeName, uecIndex );
            modelIndexSet.add( uecIndex );
            if ( uecIndex > maxUecIndex )
                maxUecIndex = uecIndex;
        }
        
        dcModel = new ChoiceModelApplication[maxUecIndex+1];

        // for each unique model index, create the ChoiceModelApplication object and the availabilty array
        Iterator<Integer> it = modelIndexSet.iterator();
        while ( it.hasNext() ) {
            int m = it.next();
            dcModel[m] = new ChoiceModelApplication ( dcUecFileName, m, DC_DATA_SHEET, propertyMap, (VariableTable)dcDmuObject );
        }


    }
    
    

    public void applyModel( Household hh ) {
        
        // declare these variables here so their values can be logged if a RuntimeException occurs.
        int i = -1;
        int purposeIndex = -1;
        String purposeName = "";

        int		 hhid = hh.getHhId();
        Person[] persons = hh.getPersons();

        for ( i=1; i < persons.length; i++ ) {

            Person p = persons[i];

            // get the individual non-mandatory tours for this person and choose a destination for each.
            int choiceNum = 0;
            ArrayList<Tour> tourList = p.getListOfIndividualNonMandatoryTours();
            for ( Tour tour : tourList ) {

                purposeName = tour.getTourPurpose();
                purposeIndex = tour.getTourPurposeIndex();

                
                int chosen = -1;
                float dcLogsum = -99;
                try {

                    int homeTaz = hh.getHhTaz();
                    int origTaz = tour.getTourOrigTaz();
                    
                    
                    // update the MC dmuObject for this person
                    mcDmuObject.setHouseholdObject( hh );
                    mcDmuObject.setPersonObject( p );
                    mcDmuObject.setTourObject( tour );
                    mcDmuObject.setDmuIndexValues( hhid, origTaz, 0 );

                    // at destination choice stage for home based individual non-mandatory tours, time of day has not been selected,
                    // use default am-pm, peak periods to compute mc logum for escort tours
                    // use default md-md, off-peak periods to compute mc logsum for other non-mandatory purpose tours
                    if ( purposeName.startsWith(modelStructure.getEscortPurposeName()) ) {
                        mcDmuObject.setTourStartHour( modelStructure.getDefaultAmHour() );
                        mcDmuObject.setTourEndHour( modelStructure.getDefaultPmHour() );                   	
                    }
                    else {
                        mcDmuObject.setTourStartHour( modelStructure.getDefaultMdHour() );
                        mcDmuObject.setTourEndHour( modelStructure.getDefaultMdHour() );                    	
                    }
                    
                    // update the DC dmuObject for this person
                    dcDmuObject.setHouseholdObject( hh );
                    dcDmuObject.setPersonObject( p );
                    dcDmuObject.setTourObject(tour);
                    dcDmuObject.setDmuIndexValues( hhid, homeTaz, origTaz, 0 );

                    // get the tour location alternative chosen from the sample
                    
                    double[] returnArray =  selectLocationFromSampleOfAlternatives( dcDmuObject, dcSoaDmuObject, mcDmuObject, tour, p, purposeName, purposeIndex, ++choiceNum );
                    chosen = (int) returnArray[0];
                    dcLogsum = (float) returnArray[1];
                }
                catch (RuntimeException e) {
                    logger.fatal( String.format("exception caught selecting %s tour destination choice for hh.hhid=%d, personNum=%d, tourId=%d, purposeIndex=%d, purposeName=%s", tourCategory, hhid, p.getPersonNum(), tour.getTourId(), purposeIndex, purposeName ) );
                    logger.fatal( "Exception caught:", e );
                    logger.fatal( "Throwing new RuntimeException() to terminate." );
                    throw new RuntimeException();
                }

                
                // get zone, subzone from DC alternative
                int chosenDestAlt = (chosen-1)/numberOfSubzones + 1;
                int chosenShrtWlk = chosen - (chosenDestAlt-1)*numberOfSubzones - 1;

                // set chosen values in tour object
                tour.setTourDestTaz( chosenDestAlt );
                tour.setTourDestWalkSubzone( chosenShrtWlk );
                tour.setDestinationChoiceLogsum(dcLogsum);
                tour.setTourStartHour( 0 );
                tour.setTourEndHour( 0 );

            }

        }

        hh.setInmtlRandomCount( hh.getHhRandomCount() );

    }

    
    

    /**
     * Select the location from a sample of alternative zones. Return it as well as the destination choice logsum.
     * 
     * @param dcDmuObject
     * @param dcSoaDmuObject
     * @param mcDmuObject
     * @param tour
     * @param person
     * @param purposeName
     * @param purposeIndex
     * @param choiceNum
     * @return A double array whose first element is the chosen zone and the second element is the destination choice logsum.
     */
    private double[] selectLocationFromSampleOfAlternatives( DestChoiceDMU dcDmuObject, DcSoaDMU dcSoaDmuObject, ModeChoiceDMU mcDmuObject, Tour tour, Person person, String purposeName, int purposeIndex, int choiceNum ) {

        int uecIndex = modelStructure.getDcUecIndexForPurpose( purposeName );

        Household household = dcDmuObject.getHouseholdObject();
        
        
        // compute the sample of alternatives set for the person
        dcSoaModel.computeDestinationSampleOfAlternatives( dcSoaDmuObject, tour, person, purposeName, purposeIndex );

        // get sample of locations and correction factors for sample
        int numUniqueAltsInSample = dcSoaModel.getNumUniqueAlts();
        int[] finalSample = dcSoaModel.getSampleOfAlternatives();
        float[] sampleCorrectionFactors = dcSoaModel.getSampleOfAlternativesCorrections();

        
        

        int numAlts = dcModel[uecIndex].getNumberOfAlternatives();

        boolean[] destAltsAvailable = new boolean[numAlts+1];
        int[] destAltsSample = new int[numAlts+1];

        // set the destAltsAvailable array to true for all destination choice alternatives for each purpose
        for (int k=1; k <= numAlts; k++)
                destAltsAvailable[k] = false;

        // set the destAltsSample array to 1 for all destination choice alternatives for each purpose
        for (int k=1; k <= numAlts; k++)
                destAltsSample[k] = 0;


        // tour start/end hours were set as default values for this tour type in method that called this method
        int logsumIndex = modelStructure.getSkimPeriodCombinationIndex( tour.getTourStartHour(), tour.getTourEndHour() );

        Logger modelLogger = dcNonManLogger;
        String choiceModelDescription = String.format ( "Individual Non-Mandatory Tour Mode Choice Logsum calculation for %s Location Choice", purposeName );
        String decisionMakerLabel = String.format ( "HH=%d, PersonNum=%d, PersonType=%s, TourId=%d", household.getHhId(), person.getPersonNum(), person.getPersonType(), tour.getTourId() );
        String loggingHeader = String.format( "%s for %s", choiceModelDescription, decisionMakerLabel );
        
        
        
        int[] sampleValues = new int[numUniqueAltsInSample+1];
        
        // for the destinations and sub-zones in the sample, compute mc logsums and save in DC dmuObject.
        // also save correction factor and set availability and sample value for the sample alternative to true. 1, respectively.
        for (int i=1; i <= numUniqueAltsInSample; i++) {

            sampleValues[i] = finalSample[i];
            
            int d = (finalSample[i]-1)/numberOfSubzones + 1;
            int w = finalSample[i] - (d-1)*numberOfSubzones - 1;
            
            // dmuIndex values have already been set, but we need to change the destination zone value, so first get the set values and chenge the one index.
            IndexValues indexValues = mcDmuObject.getDmuIndexValues();
            int origTaz = indexValues.getOriginZone();
            
            // set the zone and walk subzone for the mcDmuObject and calculate the logsum.
            mcDmuObject.setTourDestTaz( d );
            mcDmuObject.setTourDestWalkSubzone( w );

            mcDmuObject.setDmuIndexValues( household.getHhId(), origTaz, d );

            // Only log if we're doing a sample
            if ( household.getDebugChoiceModels() && (numAlts < numberOfSubzones*numberOfZones) ) {
                household.logTourObject( loggingHeader + ", sample " + i , modelLogger, person, mcDmuObject.getTourObject() );
            }
            
            // get the mode choice logsum for the destination choice sample alternative
            double logsum = mcModel.getModeChoiceLogsum( mcDmuObject, purposeName, modelLogger, choiceModelDescription, decisionMakerLabel );
            
            // set logsum value in DC dmuObject for the sampled zone and subzone - for this long term choice model, set the AmPm logsum value.
            dcDmuObject.setMcLogsum ( logsumIndex, d, w, logsum );
        
            // set sample of alternatives correction factor used in destination choice utility for the sampled alternative.
            dcDmuObject.setDcSoaCorrections( d, w, sampleCorrectionFactors[i] );

            // set availaibility and sample values for the purpose, dcAlt.
            destAltsAvailable[finalSample[i]] = true;
            destAltsSample[finalSample[i]] = 1;

        }

        
        String loggerString = "";
        String separator = "";

        // log headers to traceLogger if the person making the destination choice is from a household requesting trace information
        if ( household.getDebugChoiceModels() ) {
            
            choiceModelDescription = String.format ( "Individual Non-Mandatory Tour Destination Choice Model, Purpose=%s", purposeName );
            decisionMakerLabel = String.format ( "HH=%d, PersonNum=%d, PersonType=%s, TourId=%d", person.getHouseholdObject().getHhId(), person.getPersonNum(), person.getPersonType(), tour.getTourId() );

            modelLogger.info(" ");
            loggerString = choiceModelDescription + " for " + decisionMakerLabel + ".";
            for (int k=0; k < loggerString.length(); k++)
                separator += "+";
            modelLogger.info( loggerString );
            modelLogger.info( separator );
            modelLogger.info( "" );
            modelLogger.info( "" );
         
            dcModel[uecIndex].choiceModelUtilityTraceLoggerHeading( choiceModelDescription, decisionMakerLabel );
        
        }

        
        
        // compute destination choice proportions and choose alternative
        dcModel[uecIndex].computeUtilities ( dcDmuObject, dcDmuObject.getDmuIndexValues(), destAltsAvailable, destAltsSample );
        double dcLogsum = dcModel[uecIndex].getLogsum();
        
        Random hhRandom = household.getHhRandom();
        int randomCount = household.getHhRandomCount();
        double rn = hhRandom.nextDouble();

        
        // if the choice model has at least one available alternative, make choice.
        int chosen = -1;
        if ( dcModel[uecIndex].getAvailabilityCount() > 0 )
            chosen = dcModel[uecIndex].getChoiceResult( rn );
        else {
            logger.error ( String.format( "Exception caught for HHID=%d, PersonNum=%d, no available %s destination choice alternatives to choose from in choiceModelApplication.", dcDmuObject.getHouseholdObject().getHhId(), dcDmuObject.getPersonObject().getPersonNum(), purposeName ) );
            throw new RuntimeException();
        }
        
        
        // write choice model alternative info to log file
        if ( household.getDebugChoiceModels() ) {
            
            double[] utilities     = dcModel[uecIndex].getUtilities();
            double[] probabilities = dcModel[uecIndex].getProbabilities();
            boolean[] availabilities = dcModel[uecIndex].getAvailabilities();

            String personTypeString = person.getPersonType();
            int personNum = person.getPersonNum();

            modelLogger.info("Person num: " + personNum  + ", Person type: " + personTypeString );
            modelLogger.info("Alternative             Availability           Utility       Probability           CumProb");
            modelLogger.info("--------------------- --------------    --------------    --------------    --------------");

            int[] sortedSampleValueIndices = IndexSort.indexSort( sampleValues );
            
            double cumProb = 0.0;
            int selectedIndex = -1;
            for(int j=1; j <= numUniqueAltsInSample; j++){

                int k =  sortedSampleValueIndices[j];
                int alt = finalSample[k];

                if ( finalSample[k] == chosen )
                    selectedIndex = j;
                
                int d = ( finalSample[k]-1) / numberOfSubzones + 1;
                int w = finalSample[k] - (d-1)*numberOfSubzones - 1;
                cumProb += probabilities[alt-1];
                String altString = String.format( "%-3d %5d %5d %5d", j, alt, d, w );
                modelLogger.info(String.format("%-21s%15s%18.6e%18.6e%18.6e", altString, availabilities[alt], utilities[alt-1], probabilities[alt-1], cumProb));
            }

            modelLogger.info(" ");
            int d = (chosen-1)/numberOfSubzones + 1;
            int w = chosen - (d-1)*numberOfSubzones - 1;
            String altString = String.format( "%-3d %5d %5d %5d", selectedIndex, chosen, d, w );
            modelLogger.info( String.format("Choice: %s, with rn=%.8f, randomCount=%d", altString, rn, randomCount ) );

            modelLogger.info( separator );
            modelLogger.info( "" );
            modelLogger.info( "" );
        
            
            dcModel[uecIndex].logAlternativesInfo ( choiceModelDescription, decisionMakerLabel );
            dcModel[uecIndex].logSelectionInfo ( choiceModelDescription, decisionMakerLabel, rn, chosen );

            
            // write UEC calculation results to separate model specific log file
            dcModel[uecIndex].logUECResults( modelLogger, loggerString );
                

        }


        double[] returnArray = {(double) chosen, dcLogsum};
        return returnArray;

    }
    
    
    
    public void setModelIndex( int index ){
        modelIndex = index;
    }

    public int getModelIndex(){
        return modelIndex;
    }


    
    
    private int getSoaSampleSize( HashMap<String, String> propertyMap ) {

        String propertyValue = "";
        propertyValue = propertyMap.get( PROPERTIES_NON_MAN_SOA_SAMPLE_SIZE_KEY );
            
        int sampleSize = Integer.parseInt( propertyValue );
            
        return sampleSize; 
    }

    
}