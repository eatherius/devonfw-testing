package com.capgemini.mrchecker.webapi.core.base.driver;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;

import com.capgemini.mrchecker.test.core.logger.BFLogger;
import com.capgemini.mrchecker.webapi.core.base.properties.PropertiesFileSettings;
import com.capgemini.mrchecker.webapi.core.base.runtime.RuntimeParameters;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.opentable.extension.BodyTransformer;

import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;

public class DriverManager {
	
	private static ThreadLocal<WireMock>		driversWiremock			= new ThreadLocal<WireMock>();
	private static ThreadLocal<WireMockServer>	driversWiremockServer	= new ThreadLocal<WireMockServer>();
	
	private static PropertiesFileSettings propertiesFileSettings;
	
	@Inject
	public DriverManager(@Named("properties") PropertiesFileSettings propertiesFileSettings) {
		
		if (null == DriverManager.propertiesFileSettings) {
			DriverManager.propertiesFileSettings = propertiesFileSettings;
		}
		
		this.start();
	}
	
	public void start() {
		
		if (DriverManager.propertiesFileSettings.isVirtualServerEnabled()) {
			DriverManager.getDriverVirtualService();
		}
		DriverManager.getDriverWebAPI();
	}
	
	public void stop() {
		try {
			closeDriverVirtualServer();
			BFLogger.logDebug("Closing Driver in stop()");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		this.stop();
	}
	
	public static void clearAllDrivers() {
		driversWiremock.remove();
		driversWiremockServer.remove();
	}
	
	public static WireMock getDriverVirtualService() {
		WireMock driver = driversWiremock.get();
		if (null == driver) {
			VirtualizedService virtualizedService = createDriverVirtualServer();
			driversWiremock.set(virtualizedService.getDriver());
			driversWiremockServer.set(virtualizedService.getDriverServer());
			driver = virtualizedService.getDriver();
			
			BFLogger.logDebug("driver: " + virtualizedService.toString());
		}
		return driver;
	}
	
	public static RequestSpecification getDriverWebAPI() {
		RequestSpecification driver = createDriverWebAPI();
		BFLogger.logDebug("driver:" + driver.toString());
		return driver;
	}
	
	public static void closeDriverVirtualServer() {
		WireMock driver = driversWiremock.get();
		WireMockServer driverServer = driversWiremockServer.get();
		BFLogger.logDebug(
				"Closing communication to Server under: " + driver.toString());
		
		try {
			if (null != driver) {
				driver.shutdown();
			}
			
			if (null != driverServer) {
				driverServer.stop();
			}
			
		} catch (Exception e) {
			BFLogger.logDebug("Ooops! Something went wrong while closing the driver");
			e.printStackTrace();
		} finally {
			driversWiremock.remove();
			driversWiremockServer.remove();
		}
	}
	
	/**
	 * Method sets desired 'driver' depends on chosen parameters
	 */
	private static RequestSpecification createDriverWebAPI() {
		BFLogger.logDebug("Creating new driver.");
		RestAssured.config = new RestAssuredConfig().encoderConfig(new EncoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false));
		return given();
	}
	
	static VirtualizedService createDriverVirtualServer() {
		BFLogger.logDebug("Creating new Mock Server");
		
		VirtualizedService virtualizedService = Driver.WIREMOCK.getDriver();
		
		BFLogger.logDebug("Running: " + virtualizedService.toString());
		return virtualizedService;
	}
	
	private enum Driver {
		
		WIREMOCK {
			
			public VirtualizedService getDriver() throws FatalStartupException {
				
				WireMock driver = null;
				WireMockServerMrChecker driverServer = null;
				
				if ("".equals(getHost())) {
					WireMockConfiguration wireMockConfig = wireMockConfig().extensions(new BodyTransformer());
					wireMockConfig = setHttpPort(wireMockConfig, getPort());
					driverServer = new WireMockServerMrChecker(wireMockConfig);
					driver = driverServer.getClient();
					
					try {
						driverServer.start();
					} catch (FatalStartupException e) {
						BFLogger.logError(e.getMessage() + "host " + getHost() + ":" + getPort());
						throw new FatalStartupException(e);
					}
				} else {
					driver = new WireMock(getHost(), getPort());
				}
				
				return new VirtualizedService(driver, driverServer, getHost(), getPort());
				
			}
			
			private String getHost() {
				String hostHttp = RuntimeParameters.MOCK_HTTP_HOST.getValue();
				return hostHttp;
			}
			
			private int getPort() {
				int portHttp = RuntimeParameters.MOCK_HTTP_PORT.getValue()
						.isEmpty() ? 0 : getInteger(RuntimeParameters.MOCK_HTTP_PORT.getValue());
				return portHttp;
			}
			
			private WireMockConfiguration setHttpPort(WireMockConfiguration wireMockConfig, int portHttp) {
				if (0 != portHttp) {
					wireMockConfig.port(portHttp);
				} else {
					wireMockConfig.dynamicPort();
				}
				return wireMockConfig;
			}
			
			private int getInteger(String value) {
				int number = 0;
				try {
					number = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					BFLogger.logError("Unable convert to integer value=" + value + " Setting default value=0");
				}
				return number;
			}
			
		};
		
		public VirtualizedService getDriver() {
			return null;
		}
		
	}
}
