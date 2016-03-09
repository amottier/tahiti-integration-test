package com.bonitasoft.ut.tooling;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bdm.BusinessObjectDAOFactory;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstancesSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ProcessInstanceNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessInstanceState;
import org.bonitasoft.engine.business.data.SimpleBusinessDataReference;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.session.APISession;
import org.junit.Assert;

public class BonitaBPMAssert {

	/** ProcessAPI is used to verify process instance state, retrieve execution context... */
	private static ProcessAPI processAPI;

	/** Session is needed to access business data value */
	private static APISession session;

	/** setUp method must be call before using assertion. Recommendation is to add it to setUpClass / @BeforeClass */
	public static void setUp(APISession session, ProcessAPI processAPI) {
		BonitaBPMAssert.processAPI = processAPI;
		BonitaBPMAssert.session = session;
	}

	/**
	 * Check that a task with {@code name} is pending for process instance with id {@code processInstanceId}. If task
	 * exist it is executed on behalf of user with id {@code userId} with inputs {@code taskInputs}.
	 * 
	 * @param processInstanceId
	 *            id of the running process instance.
	 * @param string
	 *            name of the task as defined in Bonita BPm Studio.
	 * @param taskInputs
	 *            inputs that match contract definition.
	 * @param userId
	 *            id of a user existing in the test environment. You can use {@link APISession#getUserId()} to set this
	 *            value.
	 * @throws Exception
	 */
	public static void assertHumanTaskIsPendingAndExecute(long processInstanceId, String string,
			Map<String, Serializable> taskInputs, long userId) throws Exception {

		HumanTaskInstance humanTaskInstance = assertHumanTaskIsPending(processInstanceId, string);

		ProcessExecutionDriver.executePendingHumanTask(humanTaskInstance, userId, taskInputs);
	}

	public static HumanTaskInstance assertHumanTaskIsPending(long processInstanceId, String string)
			throws InterruptedException {
		waitForHumanTask();

		List<HumanTaskInstance> humanTaskInstances = processAPI.getHumanTaskInstances(processInstanceId, string, 0, 2);

		org.junit.Assert.assertEquals(1, humanTaskInstances.size());

		HumanTaskInstance humanTaskInstance = humanTaskInstances.get(0);

		return humanTaskInstance;
	}

	public static void assertProcessInstanceIsFinished(long processInstanceId) throws Exception {
		waitForProcessInstanceCompletion();

		SearchOptionsBuilder searchBuilder = new SearchOptionsBuilder(0, 100);
		searchBuilder.filter(ArchivedProcessInstancesSearchDescriptor.SOURCE_OBJECT_ID, processInstanceId);

		List<ArchivedProcessInstance> archivedProcessInstances = processAPI.searchArchivedProcessInstances(
				searchBuilder.done()).getResult();

		org.junit.Assert.assertEquals(1, archivedProcessInstances.size());
		ArchivedProcessInstance archivedProcessInstance = archivedProcessInstances.get(0);
		org.junit.Assert.assertEquals(processInstanceId, archivedProcessInstance.getSourceObjectId());
		org.junit.Assert.assertEquals(ProcessInstanceState.COMPLETED.getId(), archivedProcessInstance.getStateId());
	}

	/**
	 * Checks that business data is not null and returns the business data value.
	 * 
	 * @param businessObjectClass
	 *            business object class. E.g. VacationRequest.class.
	 * @param processInstanceId
	 *            id of the running process instance. If process instance is archived it will be used to search latest
	 *            archived process instance informations.
	 * @param businessDataName
	 *            the name of the business data as declared in the process definition in the Studio.
	 * @return the business data value.
	 * @throws Exception
	 */
	public static <T> T assertBusinessDataNotNull(Class<T> businessObjectClass, long processInstanceId,
			String businessDataName) throws Exception {

		Map<String, Serializable> processInstanceExecutionContext;
		try {
			// Retrieve process reference to business data
			processInstanceExecutionContext = processAPI.getProcessInstanceExecutionContext(processInstanceId);
		} catch (ProcessInstanceNotFoundException e) {
			Long archivedProcessInstanceId = processAPI.getFinalArchivedProcessInstance(processInstanceId).getId();
			processInstanceExecutionContext = processAPI
					.getArchivedProcessInstanceExecutionContext(archivedProcessInstanceId);
		}

		SimpleBusinessDataReference businessDataReference = (SimpleBusinessDataReference) processInstanceExecutionContext
				.get(businessDataName + "_ref");

		// Create DAO to access business data value
		BusinessObjectDAOFactory daoFactory = new BusinessObjectDAOFactory();

		@SuppressWarnings("unchecked")
		Class<? extends BusinessObjectDAO> daoClass = (Class<? extends BusinessObjectDAO>) Class
				.forName(businessObjectClass.getCanonicalName() + "DAO");

		BusinessObjectDAO businessObjectDAO = daoFactory.createDAO(session, daoClass);

		// daoClass.getConstructor(APISession.class, Class.class).newInstance(session, daoFactory);

		// Method createDAOMethod = daoClass.getMethod("findByPersistenceId", Long.class);

		// BusinessObjectDAO vacationRequestDAO = daoFactory.createDAO(session, daoClass);

		Method findByPersistenceIdMethod = daoClass.getMethod("findByPersistenceId", Long.class);

		@SuppressWarnings("unchecked")
		T businessObject = (T) findByPersistenceIdMethod
				.invoke(businessObjectDAO, businessDataReference.getStorageId());

		// VacationRequest vacationRequest =
		// vacationRequestDAO.findByPersistenceId(businessDataReference.getStorageId());

		Assert.assertNotNull(businessObject);

		return businessObject;

		// org.junit.Assert.assertEquals(expectedValue, vacationRequest.getStatus());
	}

	private static void waitForHumanTask() throws InterruptedException {
		Thread.sleep(5000);
	}

	private static void waitForProcessInstanceCompletion() throws InterruptedException {
		Thread.sleep(5000);
	}
}
