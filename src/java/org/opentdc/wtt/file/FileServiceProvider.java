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
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotAllowedException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.util.PrettyPrinter;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ResourceRefModel;
import org.opentdc.wtt.ServiceProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FileServiceProvider implements ServiceProvider {
	
	private static final String SEED_FN = "/seed.json";
	private static final String DATA_FN = "/data.json";
	private static File dataF = null;
	private static File seedF = null;

	protected static Map<String, WttCompany> companyIndex = null;
	protected static Map<String, WttProject> projectIndex = null;
	protected static ArrayList<ResourceRefModel> resources = null;
	
	// instance variables
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	// instance variables
	private boolean isPersistent = true;

	public FileServiceProvider(
		ServletContext context,
		String prefix
	) {
		if (dataF == null) {
			dataF = new File(context.getRealPath("/" + prefix + DATA_FN));
		}
		if (seedF == null) {
			seedF = new File(context.getRealPath("/" + prefix + SEED_FN));
		}
		if (companyIndex == null) {
			importJson();
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
		logger.info("listCompanies() -> " + countCompanies() + " companies");
		// Collections.sort(companies, CompanyModel.CompanyComparator);
		ArrayList<CompanyModel> _companyModels = new ArrayList<CompanyModel>();
		for (WttCompany _c : companyIndex.values()) {
			logger.info(PrettyPrinter.prettyPrintAsJSON(_c.getCompanyModel()));
			_companyModels.add(_c.getCompanyModel());
		}
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
	) throws DuplicateException {
		logger.info("createCompany(" + newCompany + ")");
		String _id = newCompany.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (companyIndex.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("company with ID " + newCompany.getId() + 
						" exists already.");
			}
		}
		newCompany.setId(_id);
		WttCompany _newCompany = new WttCompany();
		_newCompany.setCompanyModel(newCompany);
		companyIndex.put(_id, _newCompany);
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
			throw new NotFoundException("company with ID <" + id
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
		WttCompany _oldCompany = companyIndex.get(compId);
		if (_oldCompany == null) {
			throw new NotFoundException("company with ID <" + newCompany.getId()
					+ "> was not found.");
		} else {
			_oldCompany.getCompanyModel().setTitle(newCompany.getTitle());
			_oldCompany.getCompanyModel().setDescription(newCompany.getDescription());
		}
		logger.info("updateCompany() -> " + PrettyPrinter.prettyPrintAsJSON(_oldCompany.getCompanyModel()));
		if (isPersistent) {
			exportJson(dataF);
		}
		return _oldCompany.getCompanyModel();
	}

	@Override
	public void deleteCompany(
			String id) 
					throws 	NotFoundException, 
							InternalServerErrorException {
		WttCompany _company = companyIndex.get(id);
		if (_company == null) {
			throw new NotFoundException("company with ID <" + id
					+ "> was not found.");
		}
		removeProjectsFromIndexRecursively(_company.getProjects());
		if (companyIndex.remove(id) == null) {
			throw new InternalServerErrorException("company <" + id
					+ "> can not be removed, because it does not exist in the index");
		};

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
		if (companyIndex.size() != 0) {
			_count = companyIndex.size();
		}
		logger.info("countCompanies() = " + _count);
		return _count;
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
		for (WttProject _p : readWttCompany(compId).getProjects()) {
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
			exportJson(dataF);
		}
		return newProject;
	}
	
	private WttProject createWttProject(
			ProjectModel newProject)
			throws DuplicateException {
		String _id = newProject.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (projectIndex.get(_id) != null) {
				// project with same ID exists already
				throw new DuplicateException("Project with ID " + newProject.getId() + 
						" exists already.");
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
			String projId)
					throws NotFoundException {
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
			throw new NotFoundException("project with ID <" + projId
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
			throw new NotFoundException("project <" + projId + "> not found");
		} else {
			_oldProject.getProjectModel().setTitle(newProject.getTitle());
			_oldProject.getProjectModel().setDescription(newProject.getDescription());
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
			exportJson(dataF);
		}
	}
	
	@Override
	public int countProjects(
		String compId
	) {
		int _count = readWttCompany(compId).getProjects().size();
		logger.info("countProjects(" + compId + ") -> " + _count);
		return _count;
	}

	/******************************** subprojects *****************************************/
	@Override
	public List<ProjectModel> listSubprojects(String compId, String projId,
			String query, String queryType, int position, int size) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ProjectModel createSubproject(
			String compId, 
			String projId,
			ProjectModel project) 
					throws DuplicateException {
		readWttProject(projId).addProject(createWttProject(project));
		logger.info("createProjectAsSubproject(" + compId + ", " + projId + ", " + PrettyPrinter.prettyPrintAsJSON(project) + ")");
		if (isPersistent) {
			exportJson(dataF);
		}
		return project;
	}

	@Override
	public ProjectModel readSubproject(String compId, String projId,
			String subprojId) throws NotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ProjectModel updateSubproject(String compId, String projId,
			String subprojId, ProjectModel project) throws NotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteSubproject(String compId, String projId, String subprojId)
			throws NotFoundException, InternalServerErrorException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int countSubprojects(String compId, String projId) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	/******************************** resource *****************************************/
	@Override
	public List<ResourceRefModel> listResources(
			String projId,
			String query, 
			String queryType, 
			int position, 
			int size
	) {
		WttProject _p = readWttProject(projId);
		logger.info("listResources(" + projId + ") -> ");
		logger.info(PrettyPrinter.prettyPrintAsJSON(_p.getResources()));
		return _p.getResources();
	}

	// this _adds_ an existing resource to the resource list in project projId.
	// it does not create a new resource
	// the idea is to get (and administer) a resource in a separate service (e.g. AddressBook)
	@Override
	public String addResource(
			String projId, 
			String resourceId)
					throws NotFoundException, DuplicateException {
		WttProject _p = readWttProject(projId);
		// check on duplicate
		for (ResourceRefModel _resource : _p.getResources()) {
			if (_resource.getId().equals(resourceId)) {
				throw new DuplicateException(
						"Resource " + resourceId + " already exists in project" + projId);
			}
		}
		// add the resource
		ResourceRefModel _rrm = new ResourceRefModel();
		_rrm.setId(resourceId);
		// TODO: get all other instance variables from Resources-Service
		_p.addResource(_rrm);
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
		WttProject _project = readWttProject(projId);
		// get the resource resourceId from resources
		for (ResourceRefModel _resource : _project.getResources()) {
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
		WttProject _project = readWttProject(projId);
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
			WttProject project) {
		projectIndex.put(project.getProjectModel().getId(), project);
		for (WttProject _childProject : project.getProjects()) {
			indexProjectRecursively(_childProject);
		}
	}

	/**
	 * Recursively delete all subprojects from the index.
	 * 
	 * @param childProjects
	 */
	private void removeProjectsFromIndexRecursively(
			List<WttProject> childProjects) {
		for (WttProject _project : childProjects) {
			removeProjectsFromIndexRecursively(_project.getProjects());
			if ((projectIndex.remove(_project.getProjectModel().getId())) == null) {
				throw new InternalServerErrorException("project <" + _project.getProjectModel().getId()
						+ "> can not be removed, because it does not exist in the index");
			};
		}
	}
	
	private void importJson() {
		// initialize the indexes
		companyIndex = new HashMap<String, WttCompany>();
		projectIndex = new HashMap<String, WttProject>();
		resources = new ArrayList<ResourceRefModel>();
		ArrayList<WttCompany> _companies = new ArrayList<WttCompany>();
		
		// read the data file
		// either read persistent data from DATA_FN
		// or seed data from SEED_FN if no persistent data exists
		if (dataF.exists()) {
			logger.info("persistent data in file " + dataF.getName()
					+ " exists.");
			_companies = importJson(dataF);
		} else { // seeding the data
			logger.info("persistent data in file " + dataF.getName()
					+ " is missing -> seeding from " + seedF.getName());
			_companies = importJson(seedF);
		}

		// load the data into the local transient storage recursively
		int _companiesBefore = companyIndex.size();
		int _projectsBefore = projectIndex.size();
		int _resourcesBefore = resources.size();
		
		for (WttCompany _company : _companies) {
			companyIndex.put(_company.getCompanyModel().getId(), _company);
			for (WttProject _project : _company.getProjects()) {
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
		logger.info("importJson(): imported " + _companies.size()
				+ " wtt objects");
	}

	private ArrayList<WttCompany> importJson(
			File f) 
					throws NotFoundException, NotAllowedException {
		logger.info("importJson(" + f.getName() + "): importing CompanyData");
		if (!f.exists()) {
			logger.warning("importJson(" + f.getName()
					+ "): file does not exist.");
			return new ArrayList<WttCompany>();
		}
		if (!f.canRead()) {
			logger.warning("importJson(" + f.getName()
					+ "): file is not readable");
			return new ArrayList<WttCompany>();
		}
		logger.info("importJson(" + f.getName() + "): can read the file.");

		Reader _reader = null;
		ArrayList<WttCompany> _companies = null;
		try {
			_reader = new InputStreamReader(new FileInputStream(f));
			
			Gson _gson = new GsonBuilder().create();

			Type _collectionType = new TypeToken<ArrayList<WttCompany>>() {
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
			_gson.toJson(companyIndex.values(), _writer);
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

