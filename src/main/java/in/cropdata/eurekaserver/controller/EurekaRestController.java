/**
 * 
 */
package in.cropdata.eurekaserver.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.eureka.server.EurekaController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.resources.StatusResource;
import com.netflix.eureka.util.StatusInfo;

import in.cropdata.eurekaserver.model.EurekaVM;

/**
 * @author Vivek Gajbhiye - Cropdata
 *
 *         12-Feb-2020
 */
@RestController
@RequestMapping("/api/v1.0")
@CrossOrigin("*")
public class EurekaRestController {

	private ApplicationInfoManager applicationInfoManager;

	EurekaController eurekaController;

	public EurekaRestController(ApplicationInfoManager applicationInfoManager) {
		this.applicationInfoManager = applicationInfoManager;
	}

	@RequestMapping(value = "/test", method = RequestMethod.GET)
	public String testEurekaApi() {
		return "test";
	}

	private final Logger log = LoggerFactory.getLogger(EurekaRestController.class);

	/**
	 * GET /eureka/applications : get Eureka applications information
	 */
	@GetMapping("/eureka/applications")
	@Timed
	public ResponseEntity<EurekaVM> eureka() {
		EurekaVM eurekaVM = new EurekaVM();
		eurekaVM.setApplications(getApplications());
		return new ResponseEntity<>(eurekaVM, HttpStatus.OK);
	}

	private List<Map<String, Object>> getApplications() {
		List<Application> sortedApplications = getRegistry().getSortedApplications();
		ArrayList<Map<String, Object>> apps = new ArrayList<>();
		for (Application app : sortedApplications) {
			LinkedHashMap<String, Object> appData = new LinkedHashMap<>();
			apps.add(appData);
			appData.put("name", app.getName());
			List<Map<String, Object>> instances = new ArrayList<>();
			for (InstanceInfo info : app.getInstances()) {
				Map<String, Object> instance = new HashMap<>();
				instance.put("instanceId", info.getInstanceId());
				instance.put("homePageUrl", info.getHomePageUrl());
				instance.put("healthCheckUrl", info.getHealthCheckUrl());
				instance.put("statusPageUrl", info.getStatusPageUrl());
				instance.put("status", info.getStatus().name());
				instance.put("metadata", info.getMetadata());
				instances.add(instance);
			}
			appData.put("instances", instances);
		}
		return apps;
	}

	/**
	 * GET /eureka/lastn : get Eureka registrations
	 */
	@GetMapping("/eureka/lastn")
	@Timed
	public ResponseEntity<Map<String, Map<Long, String>>> lastn() {
		Map<String, Map<Long, String>> lastn = new HashMap<>();
		PeerAwareInstanceRegistryImpl registry = (PeerAwareInstanceRegistryImpl) getRegistry();
		Map<Long, String> canceledMap = new HashMap<>();
		registry.getLastNCanceledInstances().forEach(canceledInstance -> {
			canceledMap.put(canceledInstance.first(), canceledInstance.second());
		});
		lastn.put("canceled", canceledMap);
		Map<Long, String> registeredMap = new HashMap<>();
		registry.getLastNRegisteredInstances().forEach(registeredInstance -> {
			registeredMap.put(registeredInstance.first(), registeredInstance.second());
		});
		lastn.put("registered", registeredMap);
		return new ResponseEntity<>(lastn, HttpStatus.OK);
	}

	/**
	 * GET /eureka/replicas : get Eureka replicas
	 */
	@GetMapping("/eureka/replicas")
	@Timed
	public ResponseEntity<List<String>> replicas() {
		List<String> replicas = new ArrayList<>();
		getServerContext().getPeerEurekaNodes().getPeerNodesView().forEach(node -> {
			try {
				// The URL is parsed in order to remove login/password information
				URI uri = new URI(node.getServiceUrl());
				replicas.add(uri.getHost() + ":" + uri.getPort());
			} catch (URISyntaxException e) {
				log.warn("Could not parse peer Eureka node URL: {}", e.getMessage());
			}
		});

		return new ResponseEntity<>(replicas, HttpStatus.OK);
	}

	/**
	 * GET /eureka/status : get Eureka status
	 */
	@GetMapping("/eureka/status")
	@Timed
	public ResponseEntity<EurekaVM> eurekaStatus() {

		EurekaVM eurekaVM = new EurekaVM();
		eurekaVM.setStatus(getEurekaStatus());
		return new ResponseEntity<>(eurekaVM, HttpStatus.OK);
	}

	private Map<String, Object> getEurekaStatus() {
		Map<String, Object> stats = new HashMap<>();
		stats.put("time", new Date());
		stats.put("currentTime", StatusResource.getCurrentTimeAsString());
		stats.put("upTime", StatusInfo.getUpTime());
		stats.put("environment", ConfigurationManager.getDeploymentContext().getDeploymentEnvironment());
		stats.put("datacenter", ConfigurationManager.getDeploymentContext().getDeploymentDatacenter());
		PeerAwareInstanceRegistry registry = getRegistry();
		stats.put("leaseExpirationEnabled", registry.isLeaseExpirationEnabled());
		stats.put("numOfRenewsPerMinThreshold", registry.getNumOfRenewsPerMinThreshold());
		stats.put("numOfRenewsInLastMin", registry.getNumOfRenewsInLastMin());
		stats.put("isBelowRenewThreshold", registry.isBelowRenewThresold() == 1);
		stats.put("selfPreservationModeEnabled", registry.isSelfPreservationModeEnabled());
		populateInstanceInfo(stats);

		return stats;
	}

	protected void filterReplicas(Map<String, Object> model, StatusInfo statusInfo) {
		Map<String, String> applicationStats = statusInfo.getApplicationStats();
		if (applicationStats.get("registered-replicas").contains("@")) {
			applicationStats.put("registered-replicas", scrubBasicAuth(applicationStats.get("registered-replicas")));
		}
		if (applicationStats.get("unavailable-replicas").contains("@")) {
			applicationStats.put("unavailable-replicas", scrubBasicAuth(applicationStats.get("unavailable-replicas")));
		}
		if (applicationStats.get("available-replicas").contains("@")) {
			applicationStats.put("available-replicas", scrubBasicAuth(applicationStats.get("available-replicas")));
		}
		model.put("applicationStats", applicationStats);
	}

	private String scrubBasicAuth(String urlList) {
		String[] urls = urlList.split(",");
		StringBuilder filteredUrls = new StringBuilder();
		for (String u : urls) {
			if (u.contains("@")) {
				filteredUrls.append(u.substring(0, u.indexOf("//") + 2))
						.append(u.substring(u.indexOf("@") + 1, u.length())).append(",");
			} else {
				filteredUrls.append(u).append(",");
			}
		}
		return filteredUrls.substring(0, filteredUrls.length() - 1);
	}

	private void populateInstanceInfo(Map<String, Object> model) {

		StatusInfo statusInfo;
		try {
			statusInfo = new StatusResource().getStatusInfo();
		} catch (Exception e) {
			log.error(e.getMessage());
			statusInfo = StatusInfo.Builder.newBuilder().isHealthy(false).build();
		}
		if (statusInfo != null && statusInfo.getGeneralStats() != null) {
			model.put("generalStats", statusInfo.getGeneralStats());
		}
		if (statusInfo != null && statusInfo.getInstanceInfo() != null) {
			InstanceInfo instanceInfo = statusInfo.getInstanceInfo();
			Map<String, String> instanceMap = new HashMap<>();
			instanceMap.put("ipAddr", instanceInfo.getIPAddr());
			instanceMap.put("status", instanceInfo.getStatus().toString());
			if (instanceInfo.getDataCenterInfo().getName() == DataCenterInfo.Name.Amazon) {
				AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
				instanceMap.put("availabilityZone", info.get(AmazonInfo.MetaDataKey.availabilityZone));
				instanceMap.put("publicIpv4", info.get(AmazonInfo.MetaDataKey.publicIpv4));
				instanceMap.put("instanceId", info.get(AmazonInfo.MetaDataKey.instanceId));
				instanceMap.put("publicHostname", info.get(AmazonInfo.MetaDataKey.publicHostname));
				instanceMap.put("amiId", info.get(AmazonInfo.MetaDataKey.amiId));
				instanceMap.put("instanceType", info.get(AmazonInfo.MetaDataKey.instanceType));
			}
			model.put("instanceInfo", instanceMap);
			filterReplicas(model, statusInfo);
		}
	}

	@GetMapping("/eureka/appInfo")
	public ResponseEntity<Map<String, Object>> populateApps(Map<String, Object> model) {
		List<Application> sortedApplications = getRegistry().getSortedApplications();
		ArrayList<Map<String, Object>> apps = new ArrayList<>();
		for (Application app : sortedApplications) {
			LinkedHashMap<String, Object> appData = new LinkedHashMap<>();
			apps.add(appData);
			appData.put("name", app.getName());
			Map<String, Integer> amiCounts = new HashMap<>();
			Map<InstanceInfo.InstanceStatus, List<Pair<String, String>>> instancesByStatus = new HashMap<>();
			Map<String, Integer> zoneCounts = new HashMap<>();
			for (InstanceInfo info : app.getInstances()) {
				String id = info.getId();
				String url = info.getStatusPageUrl();
				InstanceInfo.InstanceStatus status = info.getStatus();
				String ami = "AmiCount";
				String zone = "";
				if (info.getDataCenterInfo().getName() == DataCenterInfo.Name.Amazon) {
					AmazonInfo dcInfo = (AmazonInfo) info.getDataCenterInfo();
					ami = dcInfo.get(AmazonInfo.MetaDataKey.amiId);
					zone = dcInfo.get(AmazonInfo.MetaDataKey.availabilityZone);
				}
				Integer count = amiCounts.get(ami);
				if (count != null) {
					amiCounts.put(ami, count + 1);
				} else {
					amiCounts.put(ami, 1);
				}
				count = zoneCounts.get(zone);
				if (count != null) {
					zoneCounts.put(zone, count + 1);
				} else {
					zoneCounts.put(zone, 1);
				}
				List<Pair<String, String>> list = instancesByStatus.get(status);
				if (list == null) {
					list = new ArrayList<>();
					instancesByStatus.put(status, list);
				}
				list.add(new Pair<>(id, url));
			}
			appData.put("amiCounts", amiCounts.entrySet());
			appData.put("zoneCounts", zoneCounts.entrySet());
			ArrayList<Map<String, Object>> instanceInfos = new ArrayList<>();
			appData.put("instanceInfos", instanceInfos);
			for (Iterator<Map.Entry<InstanceInfo.InstanceStatus, List<Pair<String, String>>>> iter = instancesByStatus
					.entrySet().iterator(); iter.hasNext();) {
				Map.Entry<InstanceInfo.InstanceStatus, List<Pair<String, String>>> entry = iter.next();
				List<Pair<String, String>> value = entry.getValue();
				InstanceInfo.InstanceStatus status = entry.getKey();
				LinkedHashMap<String, Object> instanceData = new LinkedHashMap<>();
				instanceInfos.add(instanceData);
				instanceData.put("status", entry.getKey());
				ArrayList<Map<String, Object>> instances = new ArrayList<>();
				instanceData.put("instances", instances);
				instanceData.put("isNotUp", status != InstanceInfo.InstanceStatus.UP);

				// TODO

				/*
				 * if(status != InstanceInfo.InstanceStatus.UP){
				 * buf.append("<font color=red size=+1><b>"); } buf.append("<b>").append(status
				 * .name()).append("</b> (").append(value.size()).append(") - "); if(status !=
				 * InstanceInfo.InstanceStatus.UP){ buf.append("</font></b>"); }
				 */

				for (Pair<String, String> p : value) {
					LinkedHashMap<String, Object> instance = new LinkedHashMap<>();
					instances.add(instance);
					instance.put("id", p.first());
					String url = p.second();
					instance.put("url", url);
					boolean isHref = url != null && url.startsWith("http");
					instance.put("isHref", isHref);
					/*
					 * String id = p.first(); String url = p.second(); if(url != null &&
					 * url.startsWith("http")){ buf.append("<a href=\"").append(url).append("\">");
					 * }else { url = null; } buf.append(id); if(url != null){ buf.append("</a>"); }
					 * buf.append(", ");
					 */
				}
			}
			// out.println("<td>" + buf.toString() + "</td></tr>");
		}
		model.put("apps", apps);
		return ResponseEntity.status(HttpStatus.OK).body(model);
	}

	@GetMapping("/eureka/header")
	public ResponseEntity<Map<String, Object>> populateHeader(Map<String, Object> model) {
		model.put("currentTime", StatusResource.getCurrentTimeAsString());
		model.put("upTime", StatusInfo.getUpTime());
		model.put("environment", ConfigurationManager.getDeploymentContext().getDeploymentEnvironment());
		model.put("datacenter", ConfigurationManager.getDeploymentContext().getDeploymentDatacenter());
		PeerAwareInstanceRegistry registry = getRegistry();
		model.put("leaseExpirationEnabled", registry.isLeaseExpirationEnabled());
		model.put("numOfRenewsPerMinThreshold", registry.getNumOfRenewsPerMinThreshold());
		model.put("numOfRenewsInLastMin", registry.getNumOfRenewsInLastMin());
		model.put("isBelowRenewThresold", registry.isBelowRenewThresold() == 1);
		DataCenterInfo info = applicationInfoManager.getInfo().getDataCenterInfo();
		if (info.getName() == DataCenterInfo.Name.Amazon) {
			AmazonInfo amazonInfo = (AmazonInfo) info;
			model.put("amazonInfo", amazonInfo);
			model.put("amiId", amazonInfo.get(AmazonInfo.MetaDataKey.amiId));
			model.put("availabilityZone", amazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone));
			model.put("instanceId", amazonInfo.get(AmazonInfo.MetaDataKey.instanceId));
		}
		return ResponseEntity.status(HttpStatus.OK).body(model);
	}

//	@GetMapping("/eureka/lastn")
//	@Timed
//	public Map<String, Object> lastn(Map<String, Object> model) {
//		PeerAwareInstanceRegistryImpl registry = (PeerAwareInstanceRegistryImpl) getRegistry();
//		ArrayList<Map<String, Object>> lastNCanceled = new ArrayList<>();
//		List<Pair<Long, String>> list = registry.getLastNCanceledInstances();
//		for (Pair<Long, String> entry : list) {
//			lastNCanceled.add(registeredInstance(entry.second(), entry.first()));
//		}
//		model.put("lastNCanceled", lastNCanceled);
//		list = registry.getLastNRegisteredInstances();
//		ArrayList<Map<String, Object>> lastNRegistered = new ArrayList<>();
//		for (Pair<Long, String> entry : list) {
//			lastNRegistered.add(registeredInstance(entry.second(), entry.first()));
//		}
//		model.put("lastNRegistered", lastNRegistered);
//
//		return model;
//	}

	private Map<String, Object> registeredInstance(String id, long date) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("id", id);
		map.put("date", new Date(date));
		return map;
	}

	private PeerAwareInstanceRegistry getRegistry() {
		return getServerContext().getRegistry();
	}

	private EurekaServerContext getServerContext() {
		return EurekaServerContextHolder.getInstance().getServerContext();
	}
}
