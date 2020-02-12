/**
 * 
 */
package in.cropdata.eurekaserver.rest.controller;

import java.util.List;
import java.util.Map;

/**
 * @author Vivek Gajbhiye - Cropdata
 *
 *         10-Feb-2020
 */

public class EurekaVM {

	private List<Map<String, Object>> applications;

	private Map<String, Object> status;

	public List<Map<String, Object>> getApplications() {
		return applications;
	}

	public void setApplications(List<Map<String, Object>> applications) {
		this.applications = applications;
	}

	public Map<String, Object> getStatus() {
		return status;
	}

	public void setStatus(Map<String, Object> status) {
		this.status = status;
	}
}
