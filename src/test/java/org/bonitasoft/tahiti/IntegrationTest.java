package org.bonitasoft.tahiti;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.session.APISession;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bonitasoft.ut.tooling.BonitaBPMAssert;
import com.bonitasoft.ut.tooling.DateCreator;
import com.bonitasoft.ut.tooling.ProcessExecutionDriver;
import com.bonitasoft.ut.tooling.Server;
import com.company.model.VacationRequest;
import com.company.model.VacationRequestAssert;

public class IntegrationTest {

	private static final String REJECT_COMMENTS = "Critical project milestone";

	private static final String NEW_VACATION_REQUEST = "New Vacation Request";

	private static final String INIT_VACATION_AVAILABLE = "Initiate Vacation Available";

	// private static final String CLEAN_BDM_PROCESS = "Remove All Business Data";
	//
	// private static final String CANCEL_VACATION_REQUEST = "Cancel Vacation Request";
	//
	// private static final String MODIFY_VACATION_REQUEST = "Modify Vacation Request";

	private static final String PROCESSES_VERSION = "1.2.0";

	private static APISession session;

	private static ProcessAPI processAPI;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.setProperty("server.url", "http://192.168.1.236:8080/");

		session = Server.httpConnect();
		processAPI = TenantAPIAccessor.getProcessAPI(session);

		BonitaBPMAssert.setUp(session, processAPI);
		ProcessExecutionDriver.setUp(processAPI);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		Server.logout(session);
	}

	@Before
	public void setUp() throws Exception {
		ProcessExecutionDriver.prepareServer();
	}

	@Test
	public void testHappyPath() throws Exception {
		// Create process instance to initialize vacation available
		ProcessExecutionDriver.createProcessInstance(INIT_VACATION_AVAILABLE, PROCESSES_VERSION);

		// Create process instance
		long processInstanceId = ProcessExecutionDriver.createProcessInstance(NEW_VACATION_REQUEST, PROCESSES_VERSION,
				newVacationRequestInputs());

		// Step reviewRequest
		BonitaBPMAssert.assertHumanTaskIsPendingAndExecute(processInstanceId, "Review request",
				reviewRequestApprovedInputs(), session.getUserId());

		// Check process is finished
		BonitaBPMAssert.assertProcessInstanceIsFinished(processInstanceId);
	}

	private Map<String, Serializable> newVacationRequestInputs() {
		Map<String, Serializable> submitLeaveRequestInputs = new HashMap<String, Serializable>();

		Date today = DateCreator.createToday();
		Date tomorrow = DateCreator.createTomorrow();

		submitLeaveRequestInputs.put("startDateContract", today);
		submitLeaveRequestInputs.put("returnDateContract", tomorrow);
		submitLeaveRequestInputs.put("numberOfDaysContract", Integer.valueOf(1));

		return submitLeaveRequestInputs;
	}

	private Map<String, Serializable> reviewRequestApprovedInputs() {
		Map<String, Serializable> reviewRequestInputs = new HashMap<String, Serializable>();

		reviewRequestInputs.put("statusContract", "approved");
		reviewRequestInputs.put("commentsContract", "");

		return reviewRequestInputs;
	}

	@Test
	public void testRejectPath() throws Exception {

		// Create process instance to initialize vacation available
		ProcessExecutionDriver.createProcessInstance(INIT_VACATION_AVAILABLE, PROCESSES_VERSION);

		// Create process instance
		Map<String, Serializable> newVacationRequestInputs = newVacationRequestInputs();
		long processInstanceId = ProcessExecutionDriver.createProcessInstance(NEW_VACATION_REQUEST, PROCESSES_VERSION,
				newVacationRequestInputs);

		// Check reviewRequest step is pending
		HumanTaskInstance pendingHumanTask = BonitaBPMAssert.assertHumanTaskIsPending(processInstanceId,
				"Review request");

		// Check that vacation request business data as the expected value
		VacationRequest vacationRequest = BonitaBPMAssert.assertBusinessDataNotNull(VacationRequest.class,
				processInstanceId, "vacationRequest");

		VacationRequestAssert.assertThat(vacationRequest).hasStatus("pending")
				.hasNumberOfDays((Integer) newVacationRequestInputs.get("numberOfDaysContract"))
				.hasNewRequestProcessInstanceId(processInstanceId).hasRequesterBonitaBPMId(session.getUserId())
				.hasReturnDate((Date) newVacationRequestInputs.get("returnDateContract"))
				.hasStartDate((Date) newVacationRequestInputs.get("startDateContract"));

		Assert.assertNull(vacationRequest.getReviewerBonitaBPMId());
		Assert.assertNull(vacationRequest.getComments());

		// Execute the reviewRequest step
		ProcessExecutionDriver.executePendingHumanTask(pendingHumanTask, session.getUserId(),
				reviewRequestRefusedInputs());

		// Check process is finished
		BonitaBPMAssert.assertProcessInstanceIsFinished(processInstanceId);

		// Check that vacation request business data as the expected value
		vacationRequest = BonitaBPMAssert.assertBusinessDataNotNull(VacationRequest.class, processInstanceId,
				"vacationRequest");

		VacationRequestAssert.assertThat(vacationRequest).hasStatus("refused");

	}

	private Map<String, Serializable> reviewRequestRefusedInputs() {
		Map<String, Serializable> reviewRequestInputs = new HashMap<String, Serializable>();

		reviewRequestInputs.put("statusContract", "refused");
		reviewRequestInputs.put("commentsContract", REJECT_COMMENTS);

		return reviewRequestInputs;
	}

}
