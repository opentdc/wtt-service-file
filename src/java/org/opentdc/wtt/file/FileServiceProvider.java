/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.wtt.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.opentdc.resources.ResourceModel;
import org.opentdc.service.ServiceUtil;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ProjectTreeNodeModel;
import org.opentdc.wtt.ResourceRefModel;
import org.opentdc.wtt.ServiceProvider;

public class FileServiceProvider extends AbstractFileServiceProvider<WttCompany> implements ServiceProvider {
	protected static Map<String, WttCompany> companyIndex = null;		// companyId, WttCompany
	protected static Map<String, WttProject> projectIndex = null;		// projectId, WttProject
	protected static Map<String, ResourceRefModel> resourceIndex = null;	// resourceRefId, ResourceRefModel
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context,
		String prefix
	) throws IOException {
		super(context, prefix);
		if (companyIndex == null) {
			// initialize the indexes
			companyIndex = new ConcurrentHashMap<String, WttCompany>();
			projectIndex = new ConcurrentHashMap<String, WttProject>();
			resourceIndex = new ConcurrentHashMap<String, ResourceRefModel>();
			
			List<WttCompany> _companies = importJson();
			
			// load the data into the local transient storage recursively
			for (WttCompany _company : _companies) {
				companyIndex.put(_company.getModel().getId(), _company);
				for (WttProject _project : _company.getProjects()) {
					indexProjectRecursively(_project);
				}
			}

			logger.info("indexed " 
					+ companyIndex.size() + " Companies, "
					+ projectIndex.size() + " Projects, "
					+ resourceIndex.size() + " Resources.");
		}
	}

	/******************************** company *****************************************/
	/**
	 * List all companies.
	 * 
	 * @return a list containing the companies.
	 */
	@Override
	public ArrayList<CompanyModel> listCompanies(
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		ArrayList<CompanyModel> _companies = new ArrayList<CompanyModel>();
		for (WttCompany _wttc : companyIndex.values()) {
			_companies.add(_wttc.getModel());
		}
		Collections.sort(_companies, CompanyModel.CompanyComparator);
		ArrayList<CompanyModel> _selection = new ArrayList<CompanyModel>();
		for (int i = 0; i < _companies.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_companies.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " companies.");
		return _selection;
	}

	/**
	 * Create a new company.
	 * 
	 * @param company
	 * @return the newly created company
	 * @throws DuplicateException when a company with the same id already exists
	 * @throws ValidationException when the id was generated on the client
	 */
	@Override
	public CompanyModel createCompany(
			HttpServletRequest request,
			CompanyModel company
	) throws DuplicateException, ValidationException {
		logger.info("createCompany(" + PrettyPrinter.prettyPrintAsJSON(company) + ")");
		String _id = company.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (companyIndex.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("company <" + _id + 
						"> exists already.");
			}
			else {  // a new ID was set on the client; we do not allow this
				throw new ValidationException("company <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}
		if (company.getTitle() == null || company.getTitle().isEmpty()) {
			throw new ValidationException("company <" + _id + 
					"> must contain a valid title.");
		}
		if (company.getOrgId() == null || company.getOrgId().isEmpty()) {
			throw new ValidationException("company <" + _id + 
					"> must contain a contactId.");
		}
		company.setId(_id);
		Date _date = new Date();
		company.setCreatedAt(_date);
		company.setCreatedBy(ServiceUtil.getPrincipal(request));
		company.setModifiedAt(_date);
		company.setModifiedBy(ServiceUtil.getPrincipal(request));
		WttCompany _newCompany = new WttCompany();
		_newCompany.setModel(company);
		companyIndex.put(_id, _newCompany);
		logger.info("createCompany() -> " + PrettyPrinter.prettyPrintAsJSON(company));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return company;
	}

	/**
	 * Find a company by ID.
	 * 
	 * @param id
	 *            the company ID
	 * @return the company
	 * @throws NotFoundException when no compan with this id could be found
	 *             if there exists no company with this ID
	 */
	@Override
	public CompanyModel readCompany(
			String id
	) throws NotFoundException {
		return getCompany(id);
	}
	
	public static CompanyModel getCompany(
			String id)
			throws NotFoundException {
		WttCompany _company = companyIndex.get(id);
		if (_company == null) {
			throw new NotFoundException("company <" + id
					+ "> was not found.");
		}
		logger.info("getCompany(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_company.getModel()));
		return _company.getModel();
	}
	
	
	private WttCompany readWttCompany(
			String id
	) throws NotFoundException {
		WttCompany _company = companyIndex.get(id);
		if (_company == null) {
			throw new NotFoundException("company <" + id
					+ "> was not found.");
		}
		logger.info("readWttCompany(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_company));
		return _company;
	}

	/**
	 * Update a company with new attribute values.
	 * 
	 * @param compId the ID of the company to update
	 * @param newCompany
	 *            the new version of the company
	 * @return the updated company
	 * @throws NotFoundException
	 *             if an object with the same ID did not exist
	 * @throws NotAllowedException if createdAt or createdBy was changed on the client
	 */
	@Override
	public CompanyModel updateCompany(
		HttpServletRequest request,
		String compId,
		CompanyModel newCompany
	) throws NotFoundException, ValidationException
	{
		WttCompany _c = readWttCompany(compId);
		CompanyModel _cm = _c.getModel();
		if (! _cm.getCreatedAt().equals(newCompany.getCreatedAt())) {
			logger.warning("company<" + compId + ">: ignoring createAt value <" + 
					newCompany.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _cm.getCreatedBy().equalsIgnoreCase(newCompany.getCreatedBy())) {
			logger.warning("company<" + compId + ">: ignoring createBy value <" +
					newCompany.getCreatedBy() + "> because it was set on the client.");
		}
		if (newCompany.getTitle() == null || newCompany.getTitle().isEmpty()) {
			throw new ValidationException("company <" + compId + 
					"> must contain a valid title.");
		}
		if (newCompany.getOrgId() == null || newCompany.getOrgId().isEmpty()) {
			throw new ValidationException("company <" + compId + 
					"> must contain a contactId.");
		}
		_cm.setTitle(newCompany.getTitle());
		_cm.setDescription(newCompany.getDescription());
		_cm.setOrgId(newCompany.getOrgId());
		_cm.setModifiedAt(new Date());
		_cm.setModifiedBy(ServiceUtil.getPrincipal(request));
		_c.setModel(_cm);
		companyIndex.put(compId, _c);
		logger.info("updateCompany(" + compId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_cm));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _cm;
	}

	@Override
	public void deleteCompany(
			String id) 
					throws 	NotFoundException, 
							InternalServerErrorException {
		WttCompany _c = readWttCompany(id);
		removeProjectsFromIndexRecursively(_c.getProjects());
		if (companyIndex.remove(id) == null) {
			throw new InternalServerErrorException("company <" + id
					+ "> can not be removed, because it does not exist in the index");
		};

		logger.info("deleteCompany(" + id + ")");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
	}
	
	@Override
	public ProjectTreeNodeModel readAsTree(
			String id)
			throws NotFoundException 
	{
		WttCompany _c = readWttCompany(id);
		ProjectTreeNodeModel _projectTree = new ProjectTreeNodeModel();
		_projectTree.setId(id);
		_projectTree.setTitle(_c.getModel().getTitle());
		for (WttProject _p : _c.getProjects()) {
			_projectTree.addProject(convertTree(_p));
		}
		logger.info("readAsTree(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_projectTree));
		return _projectTree;
	}
	
	private ProjectTreeNodeModel convertTree(WttProject p) {
		ProjectTreeNodeModel _node = new ProjectTreeNodeModel();
		_node.setId(p.getModel().getId());
		_node.setTitle(p.getModel().getTitle());
		for (WttProject _p : p.getProjects()) {
			_node.addProject(convertTree(_p));
		}
		for (ResourceRefModel _resource : p.getResources()) {
			_node.addResource(_resource.getId());
		}
		logger.info("convertTree(" + p.getModel().getId() + ") -> " + PrettyPrinter.prettyPrintAsJSON(_node));
		return _node;
	}

	/******************************** project *****************************************/
	/**
	 * Return the top-level projects of a company without subprojects.
	 * 
	 * @param compId the company ID
	 * @param query
	 * @param queryType
	 * @param position
	 * @param size
	 * @return all list of all top-level projects of the company
	 */
	@Override
	public ArrayList<ProjectModel> listProjects(
			String compId,
			String query, 
			String queryType, 
			int position, 
			int size
	) {
		ArrayList<ProjectModel> _projects = new ArrayList<ProjectModel>();
		for (WttProject _wttp : readWttCompany(compId).getProjects()) {
			_projects.add(_wttp.getModel());
		}
		Collections.sort(_projects, ProjectModel.ProjectComparator);
		ArrayList<ProjectModel> _selection = new ArrayList<ProjectModel>();
		for (int i = 0; i < _projects.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_projects.get(i));
			}
		}
		logger.info("listProjects(<" + compId + ">, <" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size()
				+ " values");
		return _selection;
	}
	
	@Override
	public ProjectModel createProject(
		HttpServletRequest request,
		String compId, 
		ProjectModel newProject
	) throws DuplicateException, NotFoundException, ValidationException {
		WttCompany _company = readWttCompany(compId);
		WttProject _project = createWttProject(request, newProject);
		ProjectModel _pm = _project.getModel();
		projectIndex.put(_pm.getId(), _project);
		_company.addProject(_project);
		logger.info("createProject(" + compId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_pm));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _pm;
	}
	
	private WttProject createWttProject(
			HttpServletRequest request,
			ProjectModel project)
			throws DuplicateException, ValidationException
	{
		String _id = project.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (projectIndex.get(_id) != null) {
				// project with same ID exists already
				throw new DuplicateException("project <" + project.getId() + 
						"> exists already.");
			}
			else {  // a new ID was set on the client; we do not allow this
				throw new ValidationException("project <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}
		if (project.getTitle() == null || project.getTitle().length() == 0) {
			throw new ValidationException("project <" + project.getId() +
					"> must have a valid title.");
		}

		project.setId(_id);
		Date _date = new Date();
		project.setCreatedAt(_date);
		project.setCreatedBy(ServiceUtil.getPrincipal(request));
		project.setModifiedAt(_date);
		project.setModifiedBy(ServiceUtil.getPrincipal(request));

		WttProject _newWttProject = new WttProject();
		_newWttProject.setModel(project);
		return _newWttProject;
	}

	@Override
	public ProjectModel readProject(
			String compId,
			String projId)
					throws NotFoundException {
		readWttCompany(compId);
		ProjectModel _p = readWttProject(projId).getModel();
		logger.info("readProject(" + projId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_p));
		return _p;
	}
	
	public static ProjectModel getProject(
			String projId)
			throws NotFoundException {
		ProjectModel _model = readWttProject(projId).getModel();
		logger.info("getProject(" + projId + ") -> " 
				+ PrettyPrinter.prettyPrintAsJSON(_model));
		return _model;
	}
	
	
	private static WttProject readWttProject(
			String projId)
				throws NotFoundException {
		WttProject _p = projectIndex.get(projId);
		if (_p == null) {
			throw new NotFoundException("project <" + projId
					+ "> was not found.");
		}
		return _p;
	}

	@Override
	public ProjectModel updateProject(
			HttpServletRequest request,
			String compId,
			String projId,
			ProjectModel project
	) throws NotFoundException, ValidationException {
		readWttCompany(compId);
		WttProject _wttProject = readWttProject(projId);
		ProjectModel _pm = _wttProject.getModel();
		if (! _pm.getCreatedAt().equals(project.getCreatedAt())) {
			logger.warning("project<" + projId + ">: ignoring createAt value <" 
					+ project.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _pm.getCreatedBy().equalsIgnoreCase(project.getCreatedBy())) {
			logger.warning("project<" + projId + ">: ignoring createBy value <"
					+ project.getCreatedBy() + "> because it was set on the client.");
		}
		if (project.getTitle() == null || project.getTitle().length() == 0) {
			throw new ValidationException("project <" + project.getId() +
					"> must have a valid title.");
		}
		_pm.setTitle(project.getTitle());
		_pm.setDescription(project.getDescription());
		_pm.setModifiedAt(new Date());
		_pm.setModifiedBy(ServiceUtil.getPrincipal(request));
		_wttProject.setModel(_pm);
		projectIndex.put(projId, _wttProject);
		logger.info("updateProject(" + compId + ", " + projId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_pm));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _pm;
	}

	@Override
	public void deleteProject(
		String compId, 
		String projId
	) throws NotFoundException, InternalServerErrorException {
		WttCompany _company = readWttCompany(compId);
		WttProject _project = readWttProject(projId);
		
		// 1) remove all subprojects from this project
		removeProjectsFromIndexRecursively(_project.getProjects());
		
		// 2) remove the project from the index
		if (projectIndex.remove(projId) == null) {
			throw new InternalServerErrorException("project <" + projId
					+ "> can not be removed, because it does not exist in the index.");
		}
		
		// 3) remove the project from its company (if projId is a top-level project)
		if (_company.removeProject(_project) == false) {
			// 4) remove the subproject from its parent-project (if projId is a subproject)
			if (projectIndex.remove(projId) == null) {
				throw new InternalServerErrorException("project <" + projId
						+ "> can not be removed, because it is an orphan.");
			}
		}
			
		logger.info("deleteProject(" + compId + ", " + projId + ") -> OK");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
	}

	/******************************** subprojects *****************************************/
	@Override
	public List<ProjectModel> listSubprojects(
			String compId, 
			String projId,
			String query, 
			String queryType, 
			int position, 
			int size) 
	{
		readWttCompany(compId);  	// validate existence of company
		ArrayList<ProjectModel> _subprojects = new ArrayList<ProjectModel>();
		for (WttProject _wttp : readWttProject(projId).getProjects()) {
			_subprojects.add(_wttp.getModel());
		}
		Collections.sort(_subprojects, ProjectModel.ProjectComparator);
		ArrayList<ProjectModel> _selection = new ArrayList<ProjectModel>();
		for (int i = 0; i < _subprojects.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_subprojects.get(i));
			}
		}
		logger.info("listProjects(<" + compId + ">, <" + projId + ">, <"+ query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " values");
		return _selection;
	}

	@Override
	public ProjectModel createSubproject(
			HttpServletRequest request,
			String compId, 
			String projId,
			ProjectModel project) 
					throws DuplicateException, NotFoundException, ValidationException
	{
		readWttCompany(compId);  	// validate existence of company
		WttProject _parentProject = readWttProject(projId);
		WttProject _subProject = createWttProject(request, project);
		ProjectModel _pm = _subProject.getModel();
		projectIndex.put(_pm.getId(), _subProject);
		_parentProject.addProject(_subProject);

		logger.info("createSubproject(" + compId + ", " + projId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_pm));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _pm;
	}

	@Override
	public ProjectModel readSubproject(
			String compId, 
			String projId,
			String subprojId) 
					throws NotFoundException 
	{
		readWttCompany(compId);  	// validate existence of company
		readWttProject(projId); 	// validate existence of parent project
		ProjectModel _p = readWttProject(subprojId).getModel();
		logger.info("readSubproject(" + subprojId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_p));
		return _p;
	}

	@Override
	public ProjectModel updateSubproject(
			HttpServletRequest request,
			String compId, 
			String projId,
			String subprojId, 
			ProjectModel subproject) 
					throws NotFoundException, ValidationException
	{
		readWttCompany(compId);  	// validate existence of company
		readWttProject(projId); 	// validate existence of parent project
		WttProject _wttSubProject = readWttProject(subprojId);
		ProjectModel _pm = _wttSubProject.getModel();	
		if (! _pm.getCreatedAt().equals(subproject.getCreatedAt())) {
			logger.warning("subproject<" + projId + ">: ignoring createAt value <" + 
					subproject.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _pm.getCreatedBy().equalsIgnoreCase(subproject.getCreatedBy())) {
			logger.warning("subproject<" + projId + ">: ignoring createBy value <" +
					subproject.getCreatedBy() + "> because it was set on the client.");
		}
		_pm.setTitle(subproject.getTitle());
		_pm.setDescription(subproject.getDescription());
		_pm.setModifiedAt(new Date());
		_pm.setModifiedBy(ServiceUtil.getPrincipal(request));
		_wttSubProject.setModel(_pm);
		projectIndex.put(subprojId, _wttSubProject);
		logger.info("updateSubProject(" + compId + ", " + projId + ", " + subprojId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_pm));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _pm;
	}

	@Override
	public void deleteSubproject(String compId, String projId, String subprojId)
			throws NotFoundException, InternalServerErrorException { 
		readWttCompany(compId);
		WttProject _parentProject = readWttProject(projId);
		WttProject _subProject = readWttProject(subprojId);
		
		// 1) remove all subprojects from this project
		removeProjectsFromIndexRecursively(_subProject.getProjects());
		
		// 2) remove the project from the index
		if (projectIndex.remove(subprojId) == null) {
			throw new InternalServerErrorException("subproject <" + subprojId
					+ "> can not be removed, because it does not exist in the index.");
		}
		
		// 3) remove the subproject from its parent project
		if (_parentProject.removeProject(_subProject) == false) {
			throw new InternalServerErrorException("subproject <" + subprojId
					+ "> can not be removed, because it is an orphan.");
		}
			
		logger.info("deleteSubproject(" + compId + ", " + projId + ", " + subprojId + ") -> OK");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}	
	}

	/******************************** resourceRef *****************************************/
	@Override
	public List<ResourceRefModel> listResourceRefs(
			String compId,
			String projId,
			String query, 
			String queryType, 
			int position, 
			int size
	) {
		readWttCompany(compId);		// verify existence of compId
		List<ResourceRefModel> _resources = readWttProject(projId).getResources();
		Collections.sort(_resources, ResourceRefModel.ResourceRefComparator);
		ArrayList<ResourceRefModel> _selection = new ArrayList<ResourceRefModel>();
		for (int i = 0; i < _resources.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_resources.get(i));
			}
		}		
		logger.info("listResourceRefs(" + compId + ", " + projId + ", " + query + ", " + 
				queryType + ", " + position + ", " + size + ") -> " + _selection.size()	+ " values");
		return _selection;
	}

	// this _adds_ (or creates) an existing resourceRef to the resource list in project projId.
	// it does not create a new resource
	// the idea is to get (and administer) a resource in a separate service (e.g. AddressBook)
	@Override
	public ResourceRefModel addResourceRef(
			HttpServletRequest request,
			String compId,
			String projId, 
			ResourceRefModel resourceRef)
					throws NotFoundException, DuplicateException, ValidationException {
		readWttCompany(compId);		// verify existence of compId
		WttProject _p = readWttProject(projId);
		// TODO: verify the validity of the referenced resourceId
		/*
		String _rid = resourceRef.getResourceId();
		if (_rid == null || _rid == "") {
			throw new NotFoundException("a resourceRef with empty or null id is not valid, because its resource can not be found");
			// TODO: check whether the referenced resource exists -> NotFoundException
			// TODO: verify firstName and lastName (these attributes are only cached from Resource)
		}
		*/
		String _id = resourceRef.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (resourceIndex.get(_id) != null) {
				// resourceRef with same ID exists already
				throw new DuplicateException("resourceRef <" + resourceRef.getId() +
						"> exists already.");
			}
			else {		// a new ID was set on the client; we do not allow this
				throw new ValidationException("resourceRef <" + resourceRef.getId() + 
						"> contains an ID generated on the client. This is not allowed.");
			}
		} 
		if (resourceRef.getResourceId() == null || resourceRef.getResourceId().length() == 0) {
			throw new ValidationException("resourceRef <" + resourceRef.getId() +
					"> must have a valid resourceId.");
		}
		ResourceModel _resourceModel = getResourceModel(resourceRef.getResourceId());
		resourceRef.setResourceName(_resourceModel.getName());
		
		resourceRef.setId(_id);
		Date _date = new Date();
		resourceRef.setCreatedAt(_date);
		resourceRef.setCreatedBy(ServiceUtil.getPrincipal(request));
		resourceRef.setModifiedAt(_date);
		resourceRef.setModifiedBy(ServiceUtil.getPrincipal(request));

		resourceIndex.put(_id, resourceRef);
		_p.addResource(resourceRef);
		return resourceRef;
	}
	
	private ResourceModel getResourceModel(
			String resourceId) {
		return org.opentdc.resources.file.FileServiceProvider.getResourceModel(resourceId);
	}
		
	@Override
	public void removeResourceRef(
			String compId,
			String projId, 
			String resourceId)
					throws NotFoundException, InternalServerErrorException {
		readWttCompany(compId);		// verify existence of compId
		WttProject _p = readWttProject(projId);
		if (! _p.removeResource(resourceId)) {
			throw new NotFoundException("resource <" + resourceId + "> was not found in project <" + projId + ">.");
		}
		if (resourceIndex.remove(resourceId) == null) {
			throw new InternalServerErrorException("resource <" + resourceId
					+ "> can not be removed, because it was not in the index.");
		}
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		logger.info("removeResourceRef(" + projId + ", " + resourceId + ") -> resource removed.");			
	}

	/******************************** utility methods *****************************************/
	/**
	 * Recursively add all subprojects to the index.
	 * 
	 * @param project
	 *            the new entry
	 */
	private void indexProjectRecursively(
			WttProject project) {
		projectIndex.put(project.getModel().getId(), project);
		for (WttProject _childProject : project.getProjects()) {
			indexProjectRecursively(_childProject);
		}
		for (ResourceRefModel _r : project.getResources()) {
			resourceIndex.put(_r.getId(), _r);
		}
	}

	/**
	 * Recursively delete all subprojects from the index.
	 * 
	 * @param childProjects
	 */
	private void removeProjectsFromIndexRecursively(
			List<WttProject> childProjects) {
		if (childProjects != null) {
		for (WttProject _project : childProjects) {
			removeProjectsFromIndexRecursively(_project.getProjects());
			if ((projectIndex.remove(_project.getModel().getId())) == null) {
				throw new InternalServerErrorException("project <" + _project.getModel().getId()
						+ "> can not be removed, because it does not exist in the index.");
			};
		}
		}
	}
}

