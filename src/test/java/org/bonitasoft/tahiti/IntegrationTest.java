package org.bonitasoft.tahiti;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.bonitasoft.engine.api.ApiAccessType;
//import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.LoginAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstancesSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfoCriterion;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstanceState;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.platform.LoginException;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.util.APITypeManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTest {

	private static final String TEST_USER = "walter.bates";

	/** name of the Bonita web app */
	private static final String BONITA_WEBAPP_NAME = "bonita";

	private static final String NEW_VACATION_REQUEST = "New Vacation Request";

	private static final String INIT_VACATION_AVAILABLE = "Initiate Vacation Available";

	private static final String CLEAN_BDM_PROCESS = "Remove All Business Data";

	private static final String CANCEL_VACATION_REQUEST = "Cancel Vacation Request";

	private static final String MODIFY_VACATION_REQUEST = "Modify Vacation Request";

	private static APISession session;

	private static ProcessAPI processAPI;

	@BeforeClass
	public static void setUpClass() throws Exception {
		//System.setProperty("DEV_SERVER_URL", "http://192.168.1.236:8889/");
		
		session = httpConnect();
		processAPI = TenantAPIAccessor.getProcessAPI(session);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		logout(session);
	}

	@Before
	public void setUp() throws Exception {
		prepareServer();
	}

	@Test
	public void testHappyPath() throws Exception {
		// Create process instance
		long processInstanceId = createProcessInstance(NEW_VACATION_REQUEST, "1.0", newVacationRequestInputs());

		// Step reviewRequest
		assertHumanTaskIsPendingAndExecute(processInstanceId, "Review request", reviewRequestInputs(),
				session.getUserId());

		// Check process is finished
		assertProcessInstanceIsFinished(processInstanceId);
	}

	private Map<String, Serializable> newVacationRequestInputs() {
		Map<String, Serializable> submitLeaveRequestInputs = new HashMap<String, Serializable>();

		Calendar date = new GregorianCalendar();
		date.setTimeZone(TimeZone.getTimeZone("UTC"));
		// reset hour, minutes, seconds and millis
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);

		Date today = date.getTime();

		date.add(Calendar.DAY_OF_MONTH, 1);
		Date tomorrow = date.getTime();

		submitLeaveRequestInputs.put("startDateContract", today);
		submitLeaveRequestInputs.put("returnDateContract", tomorrow);
		submitLeaveRequestInputs.put("numberOfDaysContract", Integer.valueOf(5));

		return submitLeaveRequestInputs;
	}

	private Map<String, Serializable> reviewRequestInputs() {
		Map<String, Serializable> reviewRequestInputs = new HashMap<String, Serializable>();

		reviewRequestInputs.put("statusContract", "approved");
		reviewRequestInputs.put("commentsContract", "");

		return reviewRequestInputs;
	}

	/*@Test
	public void testRejectPath() throws Exception {


		// Create process instance

		final BusinessArchive businessArchive = BusinessArchiveFactory.readBusinessArchive(new File(PROCESS_FILE_PATH));

		final ProcessDefinition processDefinition = processAPI.deployAndEnableProcess(businessArchive);

		Map<String, Serializable> submitLeaveRequestInputs = new HashMap<String, Serializable>();

		Calendar date = new GregorianCalendar();
		// reset hour, minutes, seconds and millis
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);

		Date today = date.getTime();

		date.add(Calendar.DAY_OF_MONTH, 1);
		Date tomorrow = date.getTime();

		submitLeaveRequestInputs.put("startDate", today);
		submitLeaveRequestInputs.put("returnDate", tomorrow);
		submitLeaveRequestInputs.put("numberOfDays", Long.valueOf(5));

		ProcessInstance processInstance = processAPI.startProcessWithInputs(processDefinition.getId(),
				submitLeaveRequestInputs);
		long processInstanceId = processInstance.getId();

		Thread.sleep(5000);

		// Step reviewRequest

		List<HumanTaskInstance> humanTaskInstances = processAPI.getHumanTaskInstances(processInstanceId,
				"Review request", 0, 2);

		Assert.assertEquals(1, humanTaskInstances.size());

		HumanTaskInstance reviewRequest = humanTaskInstances.get(0);

		Assert.assertEquals("Review request", reviewRequest.getName());

		Map<String, Serializable> reviewRequestInputs = new HashMap<String, Serializable>();

		reviewRequestInputs.put("status", "rejected");
		reviewRequestInputs.put("comments", "Because I don't want to approved it!");

		long reviewRequestId = reviewRequest.getId();

		processAPI.assignUserTask(reviewRequestId, session.getUserId());

		processAPI.executeUserTask(reviewRequestId, reviewRequestInputs);

		Thread.sleep(5000);

		// Check process is finished

		SearchOptionsBuilder searchBuilder = new SearchOptionsBuilder(0, 100);
		searchBuilder.filter(ArchivedProcessInstancesSearchDescriptor.SOURCE_OBJECT_ID, processInstanceId);

		List<ArchivedProcessInstance> archivedProcessInstances = processAPI.searchArchivedProcessInstances(
				searchBuilder.done()).getResult();

		Assert.assertEquals(1, archivedProcessInstances.size());
		ArchivedProcessInstance archivedProcessInstance = archivedProcessInstances.get(0);
		Assert.assertEquals(processInstanceId, archivedProcessInstance.getSourceObjectId());
		Assert.assertEquals(ProcessInstanceState.COMPLETED.getId(), archivedProcessInstance.getStateId());

		// TODO: check BDM Use LeaveRequestDAO lrdao = createDAO(session,
		// bizdata.Leaverequest.class)
		// lrdao.getById();
		// See:
		// http://documentation.bonitasoft.com/how-access-and-display-business-data-custom-page

	}*/

	private static void logout(APISession session) throws Exception {
		LoginAPI loginAPI = TenantAPIAccessor.getLoginAPI();

		loginAPI.logout(session);
	}

	// @Test
	// public void testQuick() throws Exception {
	// APISession session = httpConnect();
	//
	// processAPI = TenantAPIAccessor.getProcessAPI(session);
	//
	// List<HumanTaskInstance> humanTaskInstances = processAPI
	// .getHumanTaskInstances(1008, "Review Request",
	// 0, 2);
	//
	// Assert.assertEquals(1, humanTaskInstances.size());
	// }

	private void prepareServer() throws Exception {
		removeAllProcessInstancesAndDefinitions();
	}

	private void removeAllProcessInstancesAndDefinitions() throws Exception {
		List<ProcessDeploymentInfo> processDeploymentInfos = processAPI.getProcessDeploymentInfos(0, 100,
				ProcessDeploymentInfoCriterion.DEFAULT);

		// For all deployed process definitions
		for (ProcessDeploymentInfo processDeploymentInfo : processDeploymentInfos) {
			long processDefinitionId = processDeploymentInfo.getProcessId();

			processAPI.disableProcess(processDefinitionId);

			// Delete archived instances
			int startIndex = 0;
			long nbDeleted = 0;
			do {
				nbDeleted = processAPI.deleteArchivedProcessInstances(processDefinitionId, startIndex, 100);
			} while (nbDeleted > 0);

			// Delete running instances
			startIndex = 0;
			nbDeleted = 0;
			do {
				nbDeleted = processAPI.deleteProcessInstances(processDefinitionId, startIndex, 100);
			} while (nbDeleted > 0);

			// Enable definition
			processAPI.enableProcess(processDefinitionId);
		}
	}

	private static APISession httpConnect() throws BonitaHomeNotSetException, ServerAPIException,
			UnknownAPITypeException, LoginException {
		// Create a Map to configure Bonita Client
		Map<String, String> apiTypeManagerParams = new HashMap<>();

		// URL for server (without web app name)
		apiTypeManagerParams.put("server.url", System.getProperty("server.url"));
		
		// Bonita web application name
		apiTypeManagerParams.put("application.name", BONITA_WEBAPP_NAME);

		// Use HTTP connection to Bonita Engine API
		APITypeManager.setAPITypeAndParams(ApiAccessType.HTTP, apiTypeManagerParams);

		// Get a reference to login API and create a session for user
		// walter.bates (this user exist in default organization available in
		// Bonita Studio test environment)
		LoginAPI loginAPI = TenantAPIAccessor.getLoginAPI();

		APISession session = loginAPI.login(TEST_USER, "bpm");

		return session;
	}

	private long createProcessInstance(String processName, String processVersion,
			Map<String, Serializable> newProcessInstanceInputs) throws Exception {

		final ProcessDefinition processDefinition = processAPI.getProcessDefinition(processAPI.getProcessDefinitionId(
				processName, processVersion));

		ProcessInstance processInstance = processAPI.startProcessWithInputs(processDefinition.getId(),
				newProcessInstanceInputs);

		long processInstanceId = processInstance.getId();

		return processInstanceId;
	}

	private void assertHumanTaskIsPendingAndExecute(long processInstanceId, String string,
			Map<String, Serializable> reviewRequestInputs, long userId) throws Exception {

		waitForHumanTask();

		List<HumanTaskInstance> humanTaskInstances = processAPI.getHumanTaskInstances(processInstanceId,
				"Review request", 0, 2);

		Assert.assertEquals(1, humanTaskInstances.size());

		HumanTaskInstance reviewRequest = humanTaskInstances.get(0);

		Assert.assertEquals("Review request", reviewRequest.getName());

		long reviewRequestId = reviewRequest.getId();

		processAPI.assignUserTask(reviewRequestId, userId);

		processAPI.executeUserTask(reviewRequestId, reviewRequestInputs);

		Thread.sleep(5000);
	}

	private void assertProcessInstanceIsFinished(long processInstanceId) throws Exception {
		waitForProcessInstanceCompletion();

		SearchOptionsBuilder searchBuilder = new SearchOptionsBuilder(0, 100);
		searchBuilder.filter(ArchivedProcessInstancesSearchDescriptor.SOURCE_OBJECT_ID, processInstanceId);

		List<ArchivedProcessInstance> archivedProcessInstances = processAPI.searchArchivedProcessInstances(
				searchBuilder.done()).getResult();

		Assert.assertEquals(1, archivedProcessInstances.size());
		ArchivedProcessInstance archivedProcessInstance = archivedProcessInstances.get(0);
		Assert.assertEquals(processInstanceId, archivedProcessInstance.getSourceObjectId());
		Assert.assertEquals(ProcessInstanceState.COMPLETED.getId(), archivedProcessInstance.getStateId());
	}

	private void waitForProcessInstanceCompletion() throws InterruptedException {
		Thread.sleep(5000);

	}

	private void waitForHumanTask() throws InterruptedException {
		Thread.sleep(5000);
	}

}
