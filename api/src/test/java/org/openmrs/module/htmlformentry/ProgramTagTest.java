package org.openmrs.module.htmlformentry;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Tests of the various program-related tags
 */
public class ProgramTagTest extends BaseModuleContextSensitiveTest {
	
	protected static final String XML_DATASET_PATH = "org/openmrs/module/htmlformentry/include/";
	
	protected static final String XML_REGRESSION_TEST_DATASET = "regressionTestDataSet";
	
	PatientService ps;
	
	ProgramWorkflowService pws;
	
	@Before
	public void loadConcepts() throws Exception {
		ps = Context.getPatientService();
		pws = Context.getProgramWorkflowService();
		executeDataSet(XML_DATASET_PATH + new TestUtil().getTestDatasetFilename(XML_REGRESSION_TEST_DATASET));
	}
	
	@Test
	public void enrollInProgram_shouldSetDateEnrolledAndTheInitialStatesWhenEnrollingAPatientInAProgram() throws Exception {
		final Integer patientId = 2;
		final Integer programId = 10;
		//sanity check
		Assert.assertEquals(0,
		    pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null, null, null, null, false)
		            .size());
		final Date encounterDate = new Date();
		Calendar cal = Calendar.getInstance();
		cal.set(2012, 00, 31);
		final Date enrollmentDate = cal.getTime();
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "enrollPatientInProgramForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(enrollmentDate));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
			@Override
			public void testResults(SubmissionResults results) {
				results.assertNoErrors();
				results.assertEncounterCreated();
				List<PatientProgram> pps = pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null,
				    null, null, null, false);
				Assert.assertEquals(1, pps.size());
				
				//the user selected date should have been set
				Assert.assertEquals(ymdToDate(dateAsString(enrollmentDate)), ymdToDate(dateAsString(pps.get(0)
				        .getDateEnrolled())));
				
				//the initial state should have been set
				Assert.assertEquals(1, pps.get(0).getStates().size());
				Assert.assertEquals(200,
				    pps.get(0).getCurrentState(pws.getWorkflowByUuid("72a90efc-5140-11e1-a3e3-00248140a5eb")).getState()
				            .getId().intValue());
			};
			
		}.run();
	}
	
	@Test
	public void editPatientProgram_shouldMakeTheNecessaryChangesWhenEnrollmentDateIsChanged() throws Exception {
		executeDataSet(XML_DATASET_PATH + "ProgramTagTest-otherPatientStates.xml");
		final Integer patientId = 2;
		final Integer idForStateStartedOnEnrollmentDate = 10;
		final Integer patientProgramId = 1;
		final Date encounterDate = new Date();
		final Date enrollmentDate = new Date();
		//sanity check to make sure the test is valid
		Assert.assertTrue(OpenmrsUtil.compare(enrollmentDate, pws.getPatientProgram(patientProgramId).getDateEnrolled()) > 0);
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "editProgramDateEnrolledForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(enrollmentDate));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
			@Override
			public void testResults(SubmissionResults results) {
				results.assertNoErrors();
				results.assertEncounterCreated();
				PatientProgram pp = pws.getPatientProgram(patientProgramId);
				
				//the user selected date should have been set
				Assert.assertEquals(ymdToDate(dateAsString(enrollmentDate)), ymdToDate(dateAsString(pp.getDateEnrolled())));
				
				//the start dates for all states with start date equal to the enrollment date should have been
				//changed to the new enrollment date
				boolean foundStateStartedDuringEnrollment = false;
				boolean skippedOtherState = false;
				for (PatientState patientState : pp.getStates()) {
					if (idForStateStartedOnEnrollmentDate.equals(patientState.getId())) {
						Assert.assertEquals(ymdToDate(dateAsString(enrollmentDate)),
						    ymdToDate(dateAsString(patientState.getStartDate())));
						foundStateStartedDuringEnrollment = true;
					} else {
						Assert.assertNotSame(ymdToDate(dateAsString(enrollmentDate)),
						    ymdToDate(dateAsString(patientState.getStartDate())));
						skippedOtherState = true;
					}
				}
				Assert.assertTrue(foundStateStartedDuringEnrollment);
				Assert.assertTrue(skippedOtherState);
			};
			
		}.run();
	}
	
	@Test(expected = FormEntryException.class)
	public void enrollInProgram_shouldFailIfThereAreMultipleStatesInTheSameWorkflow() throws Exception {
		final Integer patientId = 2;
		final Date encounterDate = new Date();
		final Date enrollmentDate = new Date();
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "invalidPatientStatesForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(enrollmentDate));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
		}.run();
	}
	
	@Test
	public void editPatientProgram_shouldNotDisplayEnrollmentDateWidgetIfShowDateFalse() throws Exception {
		String htmlform = "<htmlform>Enroll In Program: <enrollInProgram programId=\"1\" showDate=\"false\" stateIds=\"2\" /><submit/></htmlform>";
		FormEntrySession session = new FormEntrySession(Context.getPatientService().getPatient(2), htmlform);
		Assert.assertTrue(session.getHtmlToDisplay().indexOf("setupDatePicker") == -1);
	}
	
	@Test
	public void enrollInProgram_shouldEnrollAPatientWhenTheStateIsDefinedByAConceptMapping() throws Exception {
		final Integer patientId = 2;
		final Integer programId = 10;
		//sanity check
		Assert.assertEquals(0,
		    pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null, null, null, null, false)
		            .size());
		
		//create a test mapping
		ConceptService cs = Context.getConceptService();
		Concept concept = cs.getConcept(10002);
		ConceptMap cm = new ConceptMap();
		cm.setSourceCode("Test Code");
		cm.setSource(cs.getConceptSourceByName("SNOMED CT"));
		cm.setCreator(Context.getAuthenticatedUser());
		cm.setDateCreated(new Date());
		concept.addConceptMapping(cm);
		cs.saveConcept(concept);
		
		final Date encounterDate = new Date();
		Calendar cal = Calendar.getInstance();
		cal.set(2012, 00, 31);
		final Date enrollmentDate = cal.getTime();
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "enrollPatientInProgramByConceptMappingForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(enrollmentDate));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
			@Override
			public void testResults(SubmissionResults results) {
				results.assertNoErrors();
				results.assertEncounterCreated();
				List<PatientProgram> pps = pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null,
				    null, null, null, false);
				Assert.assertEquals(1, pps.size());
			};
			
		}.run();
	}
	
	@Test
	public void enrollInProgram_shouldEnrollAPatientInAProgramWithMultipleWorkflowsWhenSettingSomeAndNotAllStates()
	    throws Exception {
		final Integer patientId = 2;
		final Integer programId = 10;
		//sanity check
		Assert.assertEquals(0,
		    pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null, null, null, null, false)
		            .size());
		final Date encounterDate = new Date();
		Calendar cal = Calendar.getInstance();
		cal.set(2012, 00, 31);
		final Date enrollmentDate = cal.getTime();
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "enrollPatientInProgramWithMultipleWorkflowsForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(enrollmentDate));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
			@Override
			public void testResults(SubmissionResults results) {
				results.assertNoErrors();
				results.assertEncounterCreated();
				List<PatientProgram> pps = pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null,
				    null, null, null, false);
				Assert.assertEquals(1, pps.size());
				
				//the user selected date should have been set
				Assert.assertEquals(ymdToDate(dateAsString(enrollmentDate)), ymdToDate(dateAsString(pps.get(0)
				        .getDateEnrolled())));
				
				//the initial states should have been set for the 2 workflows in the form
				Assert.assertEquals(2, pps.get(0).getCurrentStates().size());
				ProgramWorkflow wf1 = pws.getWorkflowByUuid("67337cdc-53ad-11e1-8cb6-00248140a5eb");
				ProgramWorkflow wf2 = pws.getWorkflowByUuid("6de7ed10-53ad-11e1-8cb6-00248140a5eb");
				Assert.assertNotNull(pps.get(0).getCurrentState(wf1).getState());
				Assert.assertNotNull(pps.get(0).getCurrentState(wf2).getState());
			};
			
		}.run();
	}
	
	@Test
	public void enrollInProgram_shouldNotEnrollAPatientIfAlreadyEnrolledOnTheSelectedDate() throws Exception {
		final Integer patientId = 6;
		final Integer programId = 10;
		final Patient patient = Context.getPatientService().getPatient(patientId);
		Assert.assertEquals(0, pws.getPatientPrograms(patient, pws.getProgram(programId), null, null, null, null, false)
		        .size());
		//enroll the patient in a test program
		PatientProgram pp = new PatientProgram();
		pp.setPatient(patient);
		Program program = pws.getProgram(programId);
		pp.setProgram(pws.getProgram(programId));
		final ProgramWorkflow wf = program.getWorkflow(100);
		final Date initialEnrollmentDate = new Date();
		pp.setDateEnrolled(initialEnrollmentDate);
		pp.transitionToState(wf.getState(200), initialEnrollmentDate);
		pws.savePatientProgram(pp);
		final ProgramWorkflowState originalState = pp.getCurrentState(wf).getState();
		final Integer patientProgramId = pp.getId();
		final Date completionDate = pp.getDateCompleted();
		
		//ensure the program is created
		Assert.assertEquals(1, pws.getPatientPrograms(patient, pws.getProgram(programId), null, null, null, null, false)
		        .size());
		final Date encounterDate = new Date();
		new RegressionTestHelper() {
			
			@Override
			public String getFormName() {
				return "enrollPatientInProgramForm";
			}
			
			@Override
			public Patient getPatient() {
				return ps.getPatient(patientId);
			}
			
			@Override
			public String[] widgetLabels() {
				return new String[] { "Enrollment Date:", "Encounter Date:", "Encounter Location:", "Encounter Provider:" };
			}
			
			@Override
			public void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
				//sanity check to ensure the patient is still enrolled in program
				Assert.assertNull(completionDate);
				request.addParameter(widgets.get("Enrollment Date:"), dateAsString(new Date()));
				
				request.setParameter(widgets.get("Encounter Date:"), dateAsString(encounterDate));
				request.setParameter(widgets.get("Encounter Location:"), "2");
				request.setParameter(widgets.get("Encounter Provider:"), "502");
			}
			
			@Override
			public void testResults(SubmissionResults results) {
				results.assertNoErrors();
				results.assertEncounterCreated();
				List<PatientProgram> pps = pws.getPatientPrograms(ps.getPatient(patientId), pws.getProgram(programId), null,
				    null, null, null, false);
				Assert.assertEquals(1, pps.size());
				
				//the user selected date should have been ignored
				Assert.assertEquals(ymdToDate(dateAsString(initialEnrollmentDate)), ymdToDate(dateAsString(pps.get(0)
				        .getDateEnrolled())));
				
				//The patient is still enrolled in the same program
				Assert.assertEquals(1,
				    pws.getPatientPrograms(patient, pws.getProgram(programId), null, null, null, null, false).size());
				
				//the state id in the form should have been ignored
				Assert.assertEquals(originalState, pws.getPatientProgram(patientProgramId).getCurrentState(wf).getState());
			};
			
		}.run();
	}
	
	@Test(expected = FormEntryException.class)
	public void enrollInProgram_shouldFailIfAnyOfTheStatesIsNotMarkedAsInitialAndThePatientIsNotEnrolledInTheProgram()
	    throws Exception {
		String htmlform = "<htmlform>Enroll In Program: <enrollInProgram programId=\"10\" showDate=\"true\" stateIds=\"201\" /><submit/></htmlform>";
		new FormEntrySession(Context.getPatientService().getPatient(6), htmlform);
	}
}
