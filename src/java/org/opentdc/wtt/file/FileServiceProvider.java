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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotAllowedException;
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
			companyIndex = new HashMap<String, WttCompany>();
			projectIndex = new HashMap<String, WttProject>();
			resourceIndex = new HashMap<String, ResourceRefModel>();
			
			List<WttCompany> _companies = importJson();
			
			// load the data into the local transient storage recursively
			for (WttCompany _company : _companies) {
				companyIndex.put(_company.getModel().getId(), _company);
				for (WttProject _project : _company.getProjects()) {
					indexProjectRecursively(_project);
				}
			}

			logger.info("added " 
					+ companyIndex.size() + " Companies, "
					+ projectIndex.size() + " Projects, "
					+ resourceIndex.size() + " Resources");
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
		// Collections.sort(companies, CompanyModel.CompanyComparator);
		ArrayList<CompanyModel> _companyModels = new ArrayList<CompanyModel>();
		for (WttCompany _c : companyIndex.values()) {
			logger.info(PrettyPrinter.prettyPrintAsJSON(_c.getModel()));
			_companyModels.add(_c.getModel());
		}
		logger.info("listCompanies() -> " + _companyModels.size() + " companies");
		return _companyModels;
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
		company.setId(_id);
		Date _date = new Date();
		company.setCreatedAt(_date);
		company.setCreatedBy("DUMMY_USER");
		company.setModifiedAt(_date);
		company.setModifiedBy("DUMMY_USER");
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
		CompanyModel _c = readWttCompany(id).getModel();
		logger.info("readCompany(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_c));
		return _c;
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
		String compId,
		CompanyModel newCompany
	) throws NotFoundException, NotAllowedException
	{
		WttCompany _c = readWttCompany(compId);
		CompanyModel _cm = _c.getModel();
		if (! _cm.getCreatedAt().equals(newCompany.getCreatedAt())) {
			throw new NotAllowedException("company<" + compId + ">: it is not allowed to change createAt on the client.");
		}
		if (! _cm.getCreatedBy().equalsIgnoreCase(newCompany.getCreatedBy())) {
			throw new NotAllowedException("company<" + compId + ">: it is not allowed to change createBy on the client.");
		}
		_cm.setTitle(newCompany.getTitle());
		_cm.setDescription(newCompany.getDescription());
		_cm.setModifiedAt(new Date());
		_cm.setModifiedBy("DUMMY_USER");
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
		WttCompany _c = readWttCompany(compId);
		for (WttProject _p : _c.getProjects()) {
			_projects.add(_p.getModel());
		}
		Collections.sort(_projects, ProjectModel.ProjectComparator);
		logger.info("listProjects(" + compId + ") -> " + _projects.size()
				+ " values");
		return _projects;
	}
	
	@Override
	public ProjectModel createProject(
		String compId, 
		ProjectModel newProject
	) throws DuplicateException, NotFoundException, ValidationException {
		WttCompany _company = readWttCompany(compId);
		WttProject _project = createWttProject(newProject);
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
		project.setId(_id);
		Date _date = new Date();
		project.setCreatedAt(_date);
		project.setCreatedBy("DUMMY_USER");
		project.setModifiedAt(_date);
		project.setModifiedBy("DUMMY_USER");

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
	
	private WttProject readWttProject(
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
			String compId,
			String projId,
			ProjectModel project
	) throws NotFoundException, NotAllowedException {
		readWttCompany(compId);
		WttProject _wttProject = readWttProject(projId);
		ProjectModel _pm = _wttProject.getModel();
		if (! _pm.getCreatedAt().equals(project.getCreatedAt())) {
			throw new NotAllowedException("project<" + projId + ">: it is not allowed to change createAt on the client.");
		}
		if (! _pm.getCreatedBy().equalsIgnoreCase(project.getCreatedBy())) {
			throw new NotAllowedException("project<" + projId + ">: it is not allowed to change createBy on the client.");
		}
		_pm.setTitle(project.getTitle());
		_pm.setDescription(project.getDescription());
		_pm.setModifiedAt(new Date());
		_pm.setModifiedBy("DUMMY_USER");
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
		readWttProject(projId); 	// validate existence of parent project
		ArrayList<ProjectModel> _projects = new ArrayList<ProjectModel>();
		for (WttProject _p : readWttProject(projId).getProjects()) {
			_projects.add(_p.getModel());
		}
		Collections.sort(_projects, ProjectModel.ProjectComparator);
		logger.info("listSubprojects(" + projId + ") -> " + _projects.size()
				+ " values");
		return _projects;
	}

	@Override
	public ProjectModel createSubproject(
			String compId, 
			String projId,
			ProjectModel project) 
					throws DuplicateException, NotFoundException, ValidationException
	{
		readWttCompany(compId);  	// validate existence of company
		WttProject _parentProject = readWttProject(projId);
		WttProject _subProject = createWttProject(project);
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
			String compId, 
			String projId,
			String subprojId, 
			ProjectModel subproject) 
					throws NotFoundException, NotAllowedException
	{
		readWttCompany(compId);  	// validate existence of company
		readWttProject(projId); 	// validate existence of parent project
		WttProject _wttSubProject = readWttProject(subprojId);
		ProjectModel _pm = _wttSubProject.getModel();	
		if (! _pm.getCreatedAt().equals(subproject.getCreatedAt())) {
			throw new NotAllowedException("subproject<" + projId + ">: it is not allowed to change createAt on the client.");
		}
		if (! _pm.getCreatedBy().equalsIgnoreCase(subproject.getCreatedBy())) {
			throw new NotAllowedException("subproject<" + projId + ">: it is not allowed to change createBy on the client.");
		}
		_pm.setTitle(subproject.getTitle());
		_pm.setDescription(subproject.getDescription());
		_pm.setModifiedAt(new Date());
		_pm.setModifiedBy("DUMMY_USER");
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
	public List<ResourceRefModel> listResources(
			String compId,
			String projId,
			String query, 
			String queryType, 
			int position, 
			int size
	) {
		readWttCompany(compId);		// verify existence of compId
		WttProject _p = readWttProject(projId);
		logger.info("listResources(" + projId + ") -> ");
		logger.info(PrettyPrinter.prettyPrintAsJSON(_p.getResources()));
		return _p.getResources();
	}

	// this _adds_ (or creates) an existing resourceRef to the resource list in project projId.
	// it does not create a new resource
	// the idea is to get (and administer) a resource in a separate service (e.g. AddressBook)
	@Override
	public ResourceRefModel addResource(
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
		resourceRef.setId(_id);
		Date _date = new Date();
		resourceRef.setCreatedAt(_date);
		resourceRef.setCreatedBy("DUMMY_USER");
		resourceRef.setModifiedAt(_date);
		resourceRef.setModifiedBy("DUMMY_USER");

		resourceIndex.put(_id, resourceRef);
		_p.addResource(resourceRef);
		return resourceRef;
	}
	
	@Override
	public void removeResource(
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
		logger.info("removeResource(" + projId + ", " + resourceId + ") -> resource removed.");			
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

