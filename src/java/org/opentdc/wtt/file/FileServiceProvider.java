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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
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
				companyIndex.put(_company.getCompanyModel().getId(), _company);
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
			logger.info(PrettyPrinter.prettyPrintAsJSON(_c.getCompanyModel()));
			_companyModels.add(_c.getCompanyModel());
		}
		logger.info("listCompanies() -> " + _companyModels.size() + " companies");
		return _companyModels;
	}

	/**
	 * Create a new company.
	 * 
	 * @param newCompany
	 * @return
	 * @throws InternalServerErrorException
	 */
	@Override
	public CompanyModel createCompany(
			CompanyModel newCompany
	) throws DuplicateException, ValidationException {
		logger.info("createCompany(" + PrettyPrinter.prettyPrintAsJSON(newCompany) + ")");
		String _id = newCompany.getId();
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
		newCompany.setId(_id);
		WttCompany _newCompany = new WttCompany();
		_newCompany.setCompanyModel(newCompany);
		companyIndex.put(_id, _newCompany);
		logger.info("createCompany() -> " + PrettyPrinter.prettyPrintAsJSON(newCompany));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return newCompany;
	}

	/**
	 * Find a company by ID.
	 * 
	 * @param id
	 *            the company ID
	 * @return the company
	 * @throws NotFoundException
	 *             if there exists no company with this ID
	 */
	@Override
	public CompanyModel readCompany(
			String id
	) throws NotFoundException {
		CompanyModel _c = readWttCompany(id).getCompanyModel();
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
	 * @param newCompany
	 *            the new version of the company
	 * @return the new company
	 * @throws NotFoundException
	 *             if an object with the same ID did not exist
	 */
	@Override
	public CompanyModel updateCompany(
		String compId,
		CompanyModel newCompany
	) throws NotFoundException {
		logger.info("updateCompany() -> " + PrettyPrinter.prettyPrintAsJSON(newCompany));
		WttCompany _c = readWttCompany(compId);
		_c.setTitle(newCompany.getTitle());
		_c.setDescription(newCompany.getDescription());
		logger.info("updateCompany() -> " + PrettyPrinter.prettyPrintAsJSON(_c.getCompanyModel()));
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _c.getCompanyModel();
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

	/******************************** project *****************************************/
	/**
	 * Return the top-level projects of a company without subprojects.
	 * 
	 * @param compId the company ID
	 * @return all top-level projects of a company
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
			_projects.add(_p.getProjectModel());
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
	) throws DuplicateException {
		readWttCompany(compId).addProject(createWttProject(newProject));
		logger.info("createProject(" + compId + ", " + PrettyPrinter.prettyPrintAsJSON(newProject) + ")");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return newProject;
	}
	
	private WttProject createWttProject(
			ProjectModel newProject)
			throws DuplicateException 
	{
		String _id = newProject.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (projectIndex.get(_id) != null) {
				// project with same ID exists already
				throw new DuplicateException("project <" + newProject.getId() + 
						"> exists already.");
			}
			else {  // a new ID was set on the client; we do not allow this
				throw new ValidationException("project <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}
		newProject.setId(_id);
		WttProject _newWttProject = new WttProject();
		_newWttProject.setProjectModel(newProject);
		projectIndex.put(_id, _newWttProject);
		return _newWttProject;
	}

	@Override
	public ProjectModel readProject(
			String compId,
			String projId)
					throws NotFoundException {
		readWttCompany(compId);
		ProjectModel _p = readWttProject(projId).getProjectModel();
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
			ProjectModel newProject
	) throws NotFoundException {
		WttProject _oldProject = projectIndex.get(projId);
		if (_oldProject == null) {
			throw new NotFoundException("project <" + projId + "> was not found.");
		} else {
			_oldProject.getProjectModel().setTitle(newProject.getTitle());
			_oldProject.getProjectModel().setDescription(newProject.getDescription());
		}
		logger.info("updateProject(" + compId + ", " + PrettyPrinter.prettyPrintAsJSON(_oldProject) + ") -> OK");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _oldProject.getProjectModel();
	}

	@Override
	public void deleteProject(
		String compId, 
		String projId
	) throws NotFoundException {
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
			_projects.add(_p.getProjectModel());
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
					throws DuplicateException 
	{
		readWttCompany(compId);  	// validate existence of company
		readWttProject(projId).addProject(createWttProject(project));
		logger.info("createSubproject(" + compId + ", " + projId + ", " + PrettyPrinter.prettyPrintAsJSON(project) + ")");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return project;
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
		ProjectModel _p = readWttProject(subprojId).getProjectModel();
		logger.info("readSubproject(" + subprojId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_p));
		return _p;
	}

	@Override
	public ProjectModel updateSubproject(
			String compId, 
			String projId,
			String subprojId, 
			ProjectModel project) 
					throws NotFoundException 
	{
		readWttCompany(compId);  	// validate existence of company
		readWttProject(projId); 	// validate existence of parent project
		WttProject _p = readWttProject(subprojId);
		_p.setTitle(project.getTitle());
		_p.setDescription(project.getDescription());
		logger.info("updateSubproject(" + compId + ", " + PrettyPrinter.prettyPrintAsJSON(_p) + ") -> OK");
		if (isPersistent) {
			exportJson(companyIndex.values());
		}
		return _p.getProjectModel();
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
	public String addResource(
			String compId,
			String projId, 
			ResourceRefModel resourceRef)
					throws NotFoundException, DuplicateException {
		readWttCompany(compId);		// verify existence of compId
		WttProject _p = readWttProject(projId);
		// verify the validity of the referenced resourceId
		String _rid = resourceRef.getResourceId();
		if (_rid == null || _rid == "") {
			throw new NotFoundException("a resourceRef with empty or null id is not valid, because its resource can not be found");
			// TODO: check whether the referenced resource exists -> NotFoundException
			// TODO: verify firstName and lastName (these attributes are only cached from Resource)
		}
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
		resourceIndex.put(resourceRef.getId(), resourceRef);
		_p.addResource(resourceRef);
		return _id;
	}
	
	@Override
	public void removeResource(
			String compId,
			String projId, 
			String resourceId)
					throws NotFoundException {
		readWttCompany(compId);		// verify existence of compId
		WttProject _p = readWttProject(projId);
		if (! _p.removeResource(resourceId)) {
			throw new NotFoundException("resource <" + resourceId + "> was not found in project <" + projId + ">.");
		}
		resourceIndex.remove(resourceId);
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
		projectIndex.put(project.getProjectModel().getId(), project);
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
			if ((projectIndex.remove(_project.getProjectModel().getId())) == null) {
				throw new InternalServerErrorException("project <" + _project.getProjectModel().getId()
						+ "> can not be removed, because it does not exist in the index.");
			};
		}
		}
	}
}

