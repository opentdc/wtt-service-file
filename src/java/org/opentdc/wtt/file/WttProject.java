package org.opentdc.wtt.file;
import java.util.ArrayList;
import java.util.List;

import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ResourceRefModel;

public class WttProject {
	ProjectModel projectModel;
	ArrayList<WttProject> projects;
	ArrayList<ResourceRefModel> resources;

	public WttProject() {
	}

	public ProjectModel getProjectModel() {
		return projectModel;
	}

	public void setProjectModel(ProjectModel projectModel) {
		this.projectModel = projectModel;
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

	public List<ResourceRefModel> getResources() {
		return resources;
	}

	public void setResources(ArrayList<ResourceRefModel> resources) {
		this.resources = resources;
	}
	
	public void addResource(ResourceRefModel r) {
		this.resources.add(r);
	}
	
	public boolean removeResource(ResourceRefModel r) {
		return this.resources.remove(r);
	}
}
