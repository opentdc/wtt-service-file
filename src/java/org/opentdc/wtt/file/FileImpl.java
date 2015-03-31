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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotAllowedException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ResourceModel;
import org.opentdc.wtt.ServiceProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FileImpl implements ServiceProvider {
	
	private static final String SEED_FN = "/seed.json";
	private static final String DATA_FN = "/data.json";
	private static File dataF = null;
	private static File seedF = null;

	protected static ArrayList<CompanyModel> companies = null;
	protected static Map<String, CompanyModel> companyIndex = null;
	protected static Map<String, ProjectModel> projectIndex = null;
	protected static ArrayList<String> resources = null;
	
	// instance variables
	protected Logger logger = Logger.getLogger(this.getClass().getName());

	public void initStorageProvider() {
		logger.info("> initStorageProvider()");

		if (companies == null) {
			companies = new ArrayList<CompanyModel>();
		}
		if (companyIndex == null) {
			companyIndex = new HashMap<String, CompanyModel>();
		}
		if (projectIndex == null) {
			projectIndex = new HashMap<String, ProjectModel>();
		}
		if (resources == null) {
			resources = new ArrayList<String>();
		}

		logger.info("initStorageProvider() initialized");
	}
	
	// instance variables
	private boolean isPersistent = true;

	public FileImpl(ServletContext context, boolean makePersistent) {
		logger.info("> FileImpl()");

		initStorageProvider();
		
		isPersistent = makePersistent;
		if (dataF == null) {
			dataF = new File(context.getRealPath(DATA_FN));
		}
		if (seedF == null) {
			seedF = new File(context.getRealPath(SEED_FN));
		}
		if (companyIndex.size() == 0) {
			importJson();
		}

		logger.info("FileImpl() initialized");
	}

	/******************************** company *****************************************/
	/**
	 * List all companies.
	 * 
	 * @return a list containing the companies.
	 */
	@Override
	public ArrayList<CompanyModel> listCompanies(
			boolean asTree,
			String query, 
			String queryType, 
			long position, 
			long size) {
		logger.info("listCompanies(" + asTree + ") -> " + countCompanies() + " companies");
		// internally, we keep the full data structure with all children
		// if the client want a flat structure without children, we need to filter accordingly
		ArrayList<CompanyModel> _companies = companies;
		if (asTree == false) {
			_companies = new ArrayList<CompanyModel>();
			for (CompanyModel _c : companies) {
				_companies.add(new CompanyModel(_c, false));
			}
		}
		Collections.sort(_companies, CompanyModel.CompanyComparator);
		for (CompanyModel _c : _companies) {
			logger.info(PrettyPrinter.prettyPrintAsJSON(_c));
		}
		return _companies;
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
			CompanyModel newCompany) 
					throws DuplicateException {
		if (companyIndex.get(newCompany.getId()) != null) {
			throw new DuplicateException("company with ID " + newCompany.getId() + 
					" exists already.");
		}
		companies.add(newCompany);
		indexCompany(newCompany);
		logger.info("createCompany() -> " + PrettyPrinter.prettyPrintAsJSON(newCompany));
		if (isPersistent) {
			exportJson(dataF);
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
			String id) 
					throws NotFoundException {
		CompanyModel _company = companyIndex.get(id);
		if (_company == null) {
			throw new NotFoundException("company with ID <" + id
					+ "> was not found.");
		}
		logger.info("readCompany(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_company));
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
			CompanyModel newCompany) 
					throws NotFoundException {
		CompanyModel _oldCompany = companyIndex.get(newCompany.getId());
		if (_oldCompany == null) {
			throw new NotFoundException("company with ID <" + newCompany.getId()
					+ "> was not found.");
		}
		else {
			_oldCompany.setXri(newCompany.getXri());
			_oldCompany.setTitle(newCompany.getTitle());
			_oldCompany.setDescription(newCompany.getDescription());
			removeProjectsRecursively(_oldCompany.getProjects());
			for (ProjectModel _p : newCompany.getProjects()) {
				indexProjectRecursively(_p);
			}
		}
		logger.info("updateCompany() -> " + PrettyPrinter.prettyPrintAsJSON(_oldCompany));
		if (isPersistent) {
			exportJson(dataF);
		}
		return newCompany;
	}

	@Override
	public void deleteCompany(
			String id) 
					throws 	NotFoundException, 
							InternalServerErrorException {
		CompanyModel _company = companyIndex.get(id);
		if (_company == null) {
			throw new NotFoundException("company with ID <" + id
					+ "> was not found.");
		}
		removeProjectsRecursively(_company.getProjects());
		companyIndex.remove(id);
		if (companies.remove(_company) == false) {
			throw new InternalServerErrorException("could not remove company " + id);
		}
		logger.info("deleteCompany(" + id + ")");
		if (isPersistent) {
			exportJson(dataF);
		}
	}

	/**
	 * Count all companies.
	 * 
	 * @return amount of companies or -1 if the store is not existing.
	 */
	@Override
	public int countCompanies() {
		int _count = -1;
		if (companies != null) {
			_count = companies.size();
		}
		logger.info("countCompanies() = " + _count);
		return _count;
	}
	
	private void indexCompany(CompanyModel company) {
		companyIndex.put(company.getId(), company);
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
			long position, 
			long size) {
		ArrayList<ProjectModel> _projects = new ArrayList<ProjectModel>();
		for (ProjectModel _p : readCompany(compId).getProjects()) {
			_projects.add(new ProjectModel(_p, false));
		}
		Collections.sort(_projects, ProjectModel.ProjectComparator);
		logger.info("listProjects(" + compId + ") -> " + _projects.size()
				+ " values");
		return _projects;
	}

	/**
	 * Return all projects of a company
	 * 
	 * @param compId the company ID
	 * @param asTree return the projects either as a hierarchical tree or as a flat list
	 * @return all projects of a company
	 */
	@Override
	public ArrayList<ProjectModel> listAllProjects(
			String compId, 
			boolean asTree,
			String query, 
			String queryType, 
			long position, 
			long size) {
		ArrayList<ProjectModel> _projects = readCompany(compId).getProjects();
		if (asTree == false) {
			_projects = new ArrayList<ProjectModel>();
			_projects = flatten(_projects, readCompany(compId).getProjects());
		}
		Collections.sort(_projects, ProjectModel.ProjectComparator);
		logger.info("listProjects(" + compId + ") -> " + _projects.size()
				+ " values");
		return _projects;
	}
	
	private ArrayList<ProjectModel> flatten(
			ArrayList<ProjectModel> flatList, 
			List<ProjectModel> list) {
		for (ProjectModel _p : list) {
			flatList.add(new ProjectModel(_p, false));
			flatList = flatten(flatList, _p.getProjects());
		}
		return flatList;
	}

	@Override
	public ProjectModel createProject(
			String compId, 
			ProjectModel newProject)
					throws DuplicateException {
		logger.info("createProject(" + compId + ", " + PrettyPrinter.prettyPrintAsJSON(newProject) + ")");
		if (projectIndex.get(newProject.getId()) != null) {
			// project with same ID exists already
			throw new DuplicateException(
					"Project with ID " + newProject.getId() + " exists already.");
		}
		indexProjectRecursively(newProject);
		readCompany(compId).addProject(newProject);
		if (isPersistent) {
			exportJson(dataF);
		}
		return newProject;
	}

	@Override
	public ProjectModel createProjectAsSubproject(
			String compId, 
			String projId, 
			ProjectModel newProject)
					throws DuplicateException {
		logger.info("createProjectAsSubproject(" + compId + ", " + projId + ", " + PrettyPrinter.prettyPrintAsJSON(newProject) + ")");
		if (projectIndex.get(newProject.getId()) != null) {
			// project with same ID exists already
			throw new DuplicateException(
					"Project with ID " + newProject.getId() + " exists already.");
		}
		indexProjectRecursively(newProject);
		readProject(projId).addProject(newProject);
		if (isPersistent) {
			exportJson(dataF);
		}
		return newProject;
	}

	@Override
	public ProjectModel readProject(
			String projId)
					throws NotFoundException {
		ProjectModel _project = projectIndex.get(projId);
		if (_project == null) {
			throw new NotFoundException("project with ID <" + projId
					+ "> was not found.");
		}
		logger.info("readProject(" + projId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_project));
		return _project;
	}

	@Override
	public ProjectModel updateProject(
			String compId, 
			ProjectModel newProject)
					throws NotFoundException {
		ProjectModel _oldProject = projectIndex.get(newProject.getId());
		if (_oldProject == null) {
			// object with same ID does not exist
			throw new NotFoundException("project with id <"
					+ newProject.getId() + "> was not found.");
		}
		else {
			_oldProject.setXri(newProject.getXri());
			_oldProject.setTitle(newProject.getTitle());
			_oldProject.setDescription(newProject.getDescription());
			removeProjectsRecursively(_oldProject.getProjects());
			for (ProjectModel _p : newProject.getProjects()) {
				indexProjectRecursively(_p);
			}
			_oldProject.setResources(newProject.getResources());
		}
		logger.info("updateProject(" + compId + ", " + PrettyPrinter.prettyPrintAsJSON(_oldProject) + ") -> OK");
		if (isPersistent) {
			exportJson(dataF);
		}
		return newProject;
	}

	@Override
	public void deleteProject(
			String compId, 
			String projId)
					throws NotFoundException {
		CompanyModel _company = readCompany(compId);
		ProjectModel _project = readProject(projId);
		
		// 1) remove all subprojects from this project
		removeProjectsRecursively(_project.getProjects());
		
		// 2) remove the project from the index
		projectIndex.remove(projId);
		
		// 3) remove the project from its company (if projId is a top-level project)
		_company.removeProject(projId);
		
		// 4) remove the subproject from its parent-project (if projId is a subproject)
		for (ProjectModel _p : projectIndex.values()) {
			_p.removeProject(projId);
		}
		
		logger.info("deleteProject(" + compId + ", " + projId + ") -> OK");
		if (isPersistent) {
			exportJson(dataF);
		}
	}
	
	@Override
	public int countProjects(
			String compId) {
		int _count = readCompany(compId).getProjects().size();
		logger.info("countProjects(" + compId + ") -> " + _count);
		return _count;
	}
	
	/******************************** resource *****************************************/
	@Override
	public ArrayList<ResourceModel> listResources(
			String projId,
			String query, 
			String queryType, 
			long position, 
			long size) {
		ProjectModel _project = readProject(projId);
		logger.info("listResources(" + projId + ") -> ");
		logger.info(PrettyPrinter.prettyPrintAsJSON(_project.getResources()));
		return _project.getResources();
	}

	// this _adds_ an existing resource to the resource list in project projId.
	// it does not create a new resource
	// the idea is to get (and administer) a resource in a separate service (e.g. AddressBook)
	@Override
	public String addResource(
			String projId, 
			String resourceId)
					throws NotFoundException, DuplicateException {
		ProjectModel _project = readProject(projId);
		// check on duplicate
		for (ResourceModel _resource : _project.getResources()) {
			if (_resource.getId().equals(resourceId)) {
				throw new DuplicateException(
						"Resource " + resourceId + " already exists in project" + _project.getId());
			}
		}
		// add the resource
		_project.getResources().add(new ResourceModel(resourceId));
		if (isPersistent) {
			exportJson(dataF);
		}
		return resourceId;
	}

	@Override
	public void removeResource(
			String projId, 
			String resourceId)
					throws NotFoundException {
		ProjectModel _project = readProject(projId);
		// get the resource resourceId from resources
		for (ResourceModel _resource : _project.getResources()) {
			if (_resource.getId().equals(resourceId)) {
				_project.getResources().remove(_resource);
				if (isPersistent) {
					exportJson(dataF);
				}
				logger.info("removeResource(" + projId + ", " + resourceId + ") -> resource removed.");
				return;
			}
		}
		throw new NotFoundException("Resource " + resourceId + " was not found in project " + projId);
	}

	@Override
	public int countResources(
			String projId) 
		throws NotFoundException {
		int _retVal = 0;
		ProjectModel _project = readProject(projId);
		_retVal = _project.getResources().size();
		logger.info("countResources(" + projId + ") -> " + _retVal);
		return _retVal;
	}

	/******************************** utility methods *****************************************/
	/**
	 * Recursively add all subprojects to the index.
	 * 
	 * @param project
	 *            the new entry
	 */
	private void indexProjectRecursively(
			ProjectModel project) {
		projectIndex.put(project.getId(), project);
		for (ProjectModel _childProject : project.getProjects()) {
			indexProjectRecursively(_childProject);
		}
	}

	/**
	 * Recursively delete all subprojects from the index.
	 * 
	 * @param childProjects
	 */
	private void removeProjectsRecursively(
			List<ProjectModel> childProjects) {
		for (ProjectModel _project : childProjects) {
			removeProjectsRecursively(_project.getProjects());
			projectIndex.remove(_project.getId());
		}
	}
	
	private void importJson() {
		// read the data file
		// either read persistent data from DATA_FN
		// or seed data from SEED_FN if no persistent data exists
		if (dataF.exists()) {
			logger.info("persistent data in file " + dataF.getName()
					+ " exists.");
			companies = importJson(dataF);
		} else { // seeding the data
			logger.info("persistent data in file " + dataF.getName()
					+ " is missing -> seeding from " + seedF.getName());
			companies = importJson(seedF);
		}

		// printProjectsRecursively(_wttData, "");

		// load the data into the local transient storage recursively
		int _companiesBefore = companyIndex.size();
		int _projectsBefore = projectIndex.size();
		int _resourcesBefore = resources.size();
		
		for (CompanyModel _company : companies) {
			indexCompany(_company);
			for (ProjectModel _project : _company.getProjects()) {
				indexProjectRecursively(_project);
			}
		}

		logger.info("added " 
				+ (companyIndex.size() - _companiesBefore) + " Companies, "
				+ (projectIndex.size() - _projectsBefore) + " Projects,"
				+ (resources.size() - _resourcesBefore) + " Resources");

		// create the persistent data if it did not exist
		if (isPersistent && !dataF.exists()) {
			try {
				dataF.createNewFile();
			} catch (IOException e) {
				logger.severe("importJson(): IO exception when creating file "
						+ dataF.getName());
				e.printStackTrace();
			}
			exportJson(dataF);
		}
		logger.info("importJson(): imported " + companies.size()
				+ " wtt objects");
	}

	private ArrayList<CompanyModel> importJson(
			File f) 
					throws NotFoundException, NotAllowedException {
		logger.info("importJson(" + f.getName() + "): importing CompanyData");
		if (!f.exists()) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist.");
			throw new NotFoundException("File " + f.getName()
					+ " does not exist.");
		}
		if (!f.canRead()) {
			logger.severe("importJson(" + f.getName()
					+ "): file is not readable");
			throw new NotAllowedException("File " + f.getName()
					+ " is not readable.");
		}
		logger.info("importJson(" + f.getName() + "): can read the file.");

		Reader _reader = null;
		ArrayList<CompanyModel> _companies = null;
		try {
			_reader = new InputStreamReader(new FileInputStream(f));
			
			Gson _gson = new GsonBuilder().create();

			Type _collectionType = new TypeToken<ArrayList<CompanyModel>>() {
			}.getType();
			_companies = _gson.fromJson(_reader, _collectionType);
			logger.info("importJson(" + f.getName() + "): json data converted");
		} catch (FileNotFoundException e1) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist (2).");
			e1.printStackTrace();
		} finally {
			try {
				if (_reader != null) {
					_reader.close();
				}
			} catch (IOException e) {
				logger.severe("importJson(" + f.getName()
						+ "): IOException when closing the reader.");
				e.printStackTrace();
			}
		}
		logger.info("importJson(" + f.getName() + "): " + _companies.size()
				+ " wtt objects imported.");
		return _companies;
	}

	private void exportJson(File f) {
		logger.info("exportJson(" + f.getName() + "): exporting wtt objects");

		Writer _writer = null;
		try {
			_writer = new OutputStreamWriter(new FileOutputStream(f));
			Gson _gson = new GsonBuilder().setPrettyPrinting().create();
			_gson.toJson(companies, _writer);
		} catch (FileNotFoundException e) {
			logger.severe("exportJson(" + f.getName() + "): file not found.");
			e.printStackTrace();
		} finally {
			if (_writer != null) {
				try {
					_writer.close();
				} catch (IOException e) {
					logger.severe("exportJson(" + f.getName()
							+ "): IOException when closing the reader.");
					e.printStackTrace();
				}
			}
		}
	}
}

