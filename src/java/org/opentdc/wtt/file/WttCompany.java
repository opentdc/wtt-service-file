package org.opentdc.wtt.file;

import java.util.ArrayList;
import java.util.List;

import org.opentdc.wtt.CompanyModel;

public class WttCompany {
	private CompanyModel companyModel;
	private ArrayList<WttProject> projects;

	public WttCompany() {
	}
	
	public CompanyModel getCompanyModel() {
		return companyModel;
	}
	
	public void setCompanyModel(CompanyModel companyModel) {
		this.companyModel = companyModel;
	}
	
	public List<WttProject> getProjects() {
		return projects;
	}
	
	public void setProjects(ArrayList<WttProject> projects) {
		this.projects = projects;
	}
	
	public void addProject(WttProject p) {
		this.projects.add(p);
	}
	
	public boolean removeProject(WttProject p) {
		return this.projects.remove(p);
	}
}
