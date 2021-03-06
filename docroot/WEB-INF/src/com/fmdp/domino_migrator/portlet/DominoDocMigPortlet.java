package com.fmdp.domino_migrator.portlet;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.portlet.ActionRequest;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;


import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.ACL;

import com.fmdp.bulk.domino.NotesAttachmentTaskExecutor;
import com.fmdp.domino_migrator.util.DominoProxyUtil;
import com.liferay.portal.NoSuchBackgroundTaskException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.model.BackgroundTask;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceContextFactory;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.util.bridges.mvc.MVCPortlet;

public class DominoDocMigPortlet extends MVCPortlet {
	private static Log _log = LogFactoryUtil.getLog(DominoDocMigPortlet.class);

	/**
	 * @param actionRequest
	 * @param actionResponse
	 * @throws PortletException
	 * @throws IOException
	 */
	public void saveDominoConfig(javax.portlet.ActionRequest actionRequest,
			javax.portlet.ActionResponse actionResponse)
			throws PortletException, IOException, Exception {

		String cmd = ParamUtil.getString(actionRequest, Constants.CMD);
        if (!cmd.equals(Constants.UPDATE)) {
            return;
        }
		boolean isValid = false;
		validateDominoParameters(actionRequest);
		
		// if some error was added to SessionError than the validation failed
		// in anycase we store the preferences with the value of isValid
		if (SessionErrors.isEmpty(actionRequest)) {
			isValid = true;
		}
	    PortletPreferences preferences = actionRequest.getPreferences();
		String dominoHostName = preferences.getValue("dominoHostName", StringPool.BLANK);
		String dominoUserName = preferences.getValue("dominoUserName", StringPool.BLANK);
		String dominoUserPassword = preferences.getValue("dominoUserPassword", StringPool.BLANK);
        
		String dominoDatabaseName = ParamUtil.getString(
				actionRequest, "dominoDatabaseName");
		String dominoViewName = ParamUtil.getString(
				actionRequest, "dominoViewName");
		String dominoFieldName = ParamUtil.getString(
				actionRequest, "dominoFieldName");
		String dominoFieldNameWithTags = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithTags");
		String dominoFieldNameWithCategories = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithCategories");
		String dominoFieldNameWithDescr = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithDescr");
		String dominoFieldNameWithTitle = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithTitle");
		String vocabularyName = ParamUtil.getString(
				actionRequest, "vocabularyName");

		boolean extractTags = ParamUtil.getBoolean(
				actionRequest, "extractTags");
		boolean extractCategories = ParamUtil.getBoolean(
				actionRequest, "extractCategories");
		boolean extractDescription = ParamUtil.getBoolean(
				actionRequest, "extractDescription");
		long newFolderId = ParamUtil.getLong(
				actionRequest, "newFolderId");

		if (_log.isDebugEnabled()) {
			_log.debug("saveDominoConfig - dominoHostName " + dominoHostName);
			_log.debug("saveDominoConfig - dominoUserName " + dominoUserName);
			_log.debug("saveDominoConfig - dominoUserPassword " + dominoUserPassword);
			
		}	     

	        preferences.setValue("dominoDatabaseName", dominoDatabaseName);
	        preferences.setValue("dominoViewName", dominoViewName);
	        preferences.setValue("dominoFieldName", dominoFieldName);
	        preferences.setValue("dominoFieldNameWithTags", dominoFieldNameWithTags);
	        preferences.setValue("dominoFieldNameWithCategories", dominoFieldNameWithCategories);
	        preferences.setValue("dominoFieldNameWithDescr", dominoFieldNameWithDescr);
	        preferences.setValue("dominoFieldNameWithTitle", dominoFieldNameWithTitle);
	        preferences.setValue("vocabularyName", vocabularyName);
	        preferences.setValue("extractTags", String.valueOf(extractTags));
	        preferences.setValue("extractCategories", String.valueOf(extractCategories));
	        preferences.setValue("extractDescription", String.valueOf(extractDescription));
	        preferences.setValue("newFolderId", String.valueOf(newFolderId));
	        preferences.setValue("isConfigValid", String.valueOf(isValid));
	        preferences.store();
			if (SessionErrors.isEmpty(actionRequest)) {	 
				SessionMessages.add(actionRequest, "success");
			}
	}

	/**
	 * @param actionRequest
	 * @param actionResponse
	 * @throws Exception
	 */
	public void deleteBackgroundTask(javax.portlet.ActionRequest actionRequest,
			javax.portlet.ActionResponse actionResponse)
			throws Exception {

			long backgroundTaskId = ParamUtil.getLong(
				actionRequest, "backgroundTaskId");
			if (_log.isDebugEnabled()) {			
				_log.debug("backgroundTaskId " + backgroundTaskId);
			}
			
			try {
				BackgroundTaskLocalServiceUtil.deleteBackgroundTask(backgroundTaskId);
				sendRedirect(actionRequest, actionResponse);
			}
			catch (Exception e) {
				if (e instanceof NoSuchBackgroundTaskException) {
					SessionErrors.add(actionRequest, "entryNotFound");
				}
				else if (e instanceof PrincipalException) {
					SessionErrors.add(actionRequest, "noPermissions");
				} else {
					throw e;
				}
			}
		}
	/**
	 * @param actionRequest
	 * @throws Exception
	 */
	protected void validateDominoParameters(ActionRequest actionRequest) 
			throws Exception {

	    PortletPreferences preferences = actionRequest.getPreferences();
		String dominoHostName = preferences.getValue("dominoHostName", StringPool.BLANK);
		String dominoUserName = preferences.getValue("dominoUserName", StringPool.BLANK);
		String dominoUserPassword = preferences.getValue("dominoUserPassword", StringPool.BLANK);

		String dominoDatabaseName = ParamUtil.getString(
				actionRequest, "dominoDatabaseName");
		String dominoViewName = ParamUtil.getString(
				actionRequest, "dominoViewName");
		String dominoFieldName = ParamUtil.getString(
				actionRequest, "dominoFieldName");
		String dominoFieldNameWithTags = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithTags");
		String dominoFieldNameWithCategories = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithCategories");
		String dominoFieldNameWithDescr = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithDescr");
		String dominoFieldNameWithTitle = ParamUtil.getString(
				actionRequest, "dominoFieldNameWithTitle");
		String vocabularyName = ParamUtil.getString(
				actionRequest, "vocabularyName");
		boolean extractTags = ParamUtil.getBoolean(
				actionRequest, "extractTags");
		boolean extractCategories = ParamUtil.getBoolean(
				actionRequest, "extractCategories");
		boolean extractDescription = ParamUtil.getBoolean(
				actionRequest, "extractDescription");
		/*
		 * 
		 * START: required fields
		 * 
		 */
		if (Validator.isNull(dominoHostName)) {
			SessionErrors.add(actionRequest, "dominoServerNameRequired");
		}
		if (Validator.isNull(dominoUserName)) {
			SessionErrors.add(actionRequest, "dominoUserNameRequired");
		}
		if (Validator.isNull(dominoUserPassword)) {
			SessionErrors.add(actionRequest, "dominoUserPasswordRequired");
		}
		if (Validator.isNull(dominoDatabaseName)) {
			SessionErrors.add(actionRequest, "dominoDatabaseNameRequired");
		}
		if (Validator.isNull(dominoViewName)) {
			SessionErrors.add(actionRequest, "dominoViewNameRequired");
		}
		if (Validator.isNull(dominoFieldName)) {
			SessionErrors.add(actionRequest, "dominoFieldNameRequired");
		}
		if (Validator.isNull(dominoFieldNameWithTitle)) {
			SessionErrors.add(actionRequest, "dominoFieldNameWithTitleRequired");
		}
		if (extractTags && Validator.isNull(dominoFieldNameWithTags)) {
			SessionErrors.add(actionRequest, "dominoFieldNameWithTagsRequired");
		}
		if (extractCategories && Validator.isNull(dominoFieldNameWithCategories)) {
			SessionErrors.add(actionRequest, "dominoFieldNameWithCategoriesRequired");
		}

		if (extractCategories && Validator.isNull(vocabularyName)) {
			SessionErrors.add(actionRequest, "vocabularyNameRequired");
		}

		if (extractDescription && Validator.isNull(dominoFieldNameWithDescr)) {
			SessionErrors.add(actionRequest, "dominoFieldNameWithDescrRequired");
		}
		/*
		 * 
		 * END: required fields
		 * 
		 */

		/*
		 * Now if all the required field values are available 
		 * we try to connect to Lotus Domino Server
		 */
		if (SessionErrors.isEmpty(actionRequest)) {
			DominoProxyUtil dominoProxy = DominoProxyUtil.getInstance();
			dominoProxy.openDominoSession(dominoHostName, dominoUserName, dominoUserPassword);
			if (!dominoProxy.isDominoSessionAvailable()) {
				SessionErrors.add(actionRequest, "noDominoSessionAvalaible");
				return;
			}
			String server = dominoProxy.dominoSession.getServerName();
			Database db = dominoProxy.dominoSession.getDatabase(server, dominoDatabaseName);
			if (!db.isOpen()) {
				SessionErrors.add(actionRequest, "dominoDatabaseUnavalaible");
				return;
			}
			
			/*
			 * Here we get the db ACL role list (if exists) 
			 * and we store it in the preference for future uses
			 */
			ACL acl = db.getACL();
			Vector<?>  roles = acl.getRoles();
		    String theRoles = StringUtil.merge(roles);
		    preferences.setValue("dominoDatabaseAcl", theRoles);
		    preferences.store();
		    
		    /*
		     * Notes View checking
		     */
			View view = db.getView(dominoViewName);
			if (view == null ) {
				SessionErrors.add(actionRequest, "dominoViewUnavalaible");
				return;
			}
			dominoProxy.closeDominoSession();
		}
		
	}
	/**
	 * @param actionRequest
	 * @param actionResponse
	 * @throws PortletException
	 * @throws IOException
	 */
	public void startTask(javax.portlet.ActionRequest actionRequest,
			javax.portlet.ActionResponse actionResponse)
			throws PortletException, IOException {
        String cmd = ParamUtil.getString(actionRequest, Constants.CMD);

        if (!cmd.equals(Constants.UPDATE)) {
            return;
        }
        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        String portletId = PortalUtil.getPortletId(actionRequest);

        /*
         * We need the serviceContext for the AddBackgroundTask
         */
        ServiceContext serviceContext = null;
		try {
			serviceContext = ServiceContextFactory.getInstance(actionRequest);
		} catch (PortalException e) {
			e.printStackTrace();
			SessionErrors.add(
					actionRequest, "errorGettingServiceContext");
			return;
		} catch (SystemException e) {
			e.printStackTrace();
			SessionErrors.add(
					actionRequest, "errorGettingServiceContext");
			return;
		}

        /*
         * We need the servletContextNames for the AddBackgroundTask
         */

		HttpServletRequest request = PortalUtil.getHttpServletRequest(actionRequest);
		ServletContext servletContext = request.getSession().getServletContext();
		
		String[] servletContextNames = new String[1];
		servletContextNames[0] = servletContext.getServletContextName();

		/*
		 * We had to prepare the taskContextMap
		 * we get the attributes and their values from the portletPreferences
		 */
		PortletPreferences preferences = actionRequest.getPreferences();		
		String dominoHostName = preferences.getValue("dominoHostName", StringPool.BLANK);
		String dominoUserName = preferences.getValue("dominoUserName", StringPool.BLANK);
		String dominoUserPassword = preferences.getValue("dominoUserPassword", StringPool.BLANK);
		String dominoDatabaseName = preferences.getValue("dominoDatabaseName", StringPool.BLANK);
		String dominoViewName = preferences.getValue("dominoViewName", StringPool.BLANK);
		String dominoFieldName = preferences.getValue("dominoFieldName", StringPool.BLANK);
		String dominoFieldNameWithTags = preferences.getValue("dominoFieldNameWithTags", StringPool.BLANK);
		String dominoFieldNameWithCategories = preferences.getValue("dominoFieldNameWithCategories", StringPool.BLANK);
		String dominoFieldNameWithDescr = preferences.getValue("dominoFieldNameWithDescr", StringPool.BLANK);
		String dominoFieldNameWithTitle = preferences.getValue("dominoFieldNameWithTitle", StringPool.BLANK);
		String vocabularyName = preferences.getValue("vocabularyName", StringPool.BLANK);
		boolean extractTags = GetterUtil.getBoolean(preferences.getValue("extractTags", StringPool.BLANK));
		boolean extractCategories = GetterUtil.getBoolean(preferences.getValue("extractCategories", StringPool.BLANK));
		boolean extractDescription = GetterUtil.getBoolean(preferences.getValue("extractDescription", StringPool.BLANK));
		long newFolderId = GetterUtil.getLong(preferences.getValue("newFolderId", StringPool.BLANK));
		
		Map<String, Serializable> taskContextMap = new HashMap<String, Serializable>();
		taskContextMap.put("portletId", portletId);
		taskContextMap.put("dominoHostName", dominoHostName);
		taskContextMap.put("dominoUserName", dominoUserName);
		taskContextMap.put("dominoUserPassword", dominoUserPassword);
		taskContextMap.put("dominoDatabaseName", dominoDatabaseName);
		taskContextMap.put("dominoViewName", dominoViewName);
		taskContextMap.put("dominoFieldName", dominoFieldName);
		taskContextMap.put("dominoFieldNameWithTags", dominoFieldNameWithTags);
		taskContextMap.put("dominoFieldNameWithCategories", dominoFieldNameWithCategories);
		taskContextMap.put("dominoFieldNameWithDescr", dominoFieldNameWithDescr);
		taskContextMap.put("dominoFieldNameWithTitle", dominoFieldNameWithTitle);
		taskContextMap.put("vocabularyName", vocabularyName);
		taskContextMap.put("extractTags", extractTags);
		taskContextMap.put("extractCategories", extractCategories);
		taskContextMap.put("extractDescription", extractDescription);
		taskContextMap.put("newFolderId", newFolderId);	
		taskContextMap.put("groupId", themeDisplay.getScopeGroupId());
		taskContextMap.put("userId", themeDisplay.getUserId());
		taskContextMap.put("locale", themeDisplay.getLocale().getLanguage() + "," + themeDisplay.getLocale().getCountry());
		
        try {
        	BackgroundTask backgroundTask = BackgroundTaskLocalServiceUtil.addBackgroundTask(themeDisplay.getUserId(), themeDisplay.getSiteGroupId(), 
					StringPool.BLANK, servletContextNames, NotesAttachmentTaskExecutor.class, 
					taskContextMap, serviceContext);
        	/*
        	 * we get the backgroundTaskId and we pass it to the portlet web
        	 */
        	actionRequest.setAttribute("backgroundTaskId", backgroundTask.getBackgroundTaskId());
        	
		} catch (PortalException e) {
			e.printStackTrace();
			SessionErrors.add(
					actionRequest, "errorStartingBackgroundTask");
			return;
		} catch (SystemException e) {
			e.printStackTrace();
			SessionErrors.add(
					actionRequest, "errorStartingBackgroundTask");
			return;
		}

	        SessionMessages.add(actionRequest, "successTaskStarted");

	}
	@Override
	public void serveResource(
		ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws PortletException, IOException {

		PortletContext portletContext = getPortletContext();
		
		PortletRequestDispatcher portletRequestDispatcher = null;
		portletRequestDispatcher = portletContext.getRequestDispatcher(
				"/import_domino_processes.jsp");
		portletRequestDispatcher.include(resourceRequest, resourceResponse);
	}
	
}
