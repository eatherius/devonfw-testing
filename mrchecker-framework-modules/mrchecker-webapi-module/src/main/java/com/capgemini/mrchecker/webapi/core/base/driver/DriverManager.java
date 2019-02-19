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
	
	private static ThreadLocal<VirtualizedService> driverVirtualizedService = new ThreadLocal<VirtualizedService>();
	
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
		driverVirtualizedService.remove();
	}
	
	public static WireMock getDriverVirtualService() {
		VirtualizedService virtualizedService = getVirtualizedService();
		WireMock driver = virtualizedService.getDriver();
		BFLogger.logDebug("Driver for: " + virtualizedService.toString());
		return driver;
	}
	
	public static int getHttpPort() {
		VirtualizedService virtualizedService = getVirtualizedService();
		return virtualizedService.getHttpPort();
	}
	
	public static String getHttpHost() {
		VirtualizedService virtualizedService = getVirtualizedService();
		return virtualizedService.getHttpHost();
	}
	
	private static VirtualizedService getVirtualizedService() {
		VirtualizedService virtualizedService = driverVirtualizedService.get();
		if (null == virtualizedService) {
			virtualizedService = createDriverVirtualServer();
			driverVirtualizedService.set(virtualizedService);
		}
		return virtualizedService;
	}
	
	public static RequestSpecification getDriverWebAPI() {
		RequestSpecification driver = createDriverWebAPI();
		BFLogger.logDebug("driver:" + driver.toString());
		return driver;
	}
	
	public static void closeDriverVirtualServer() {
		VirtualizedService virtualizedService = driverVirtualizedService.get();
		
		if (null != virtualizedService) {
			WireMock driver = virtualizedService.getDriver();
			WireMockServer driverServer = virtualizedService.getDriverServer();
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
				driverVirtualizedService.remove();
			}
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
			
			int		httpPort	= -1;
			String	httpHost;
			
			public VirtualizedService getDriver() throws FatalStartupException {
				
				WireMock driver = null;
				WireMockServerMrChecker driverServer = null;
				
				if ("".equals(getHost()) || "http://localhost".equals(getHost()) || "https://localhost".equals(getHost())) {
					WireMockConfiguration wireMockConfig = wireMockConfig().extensions(new BodyTransformer());
					wireMockConfig = setHttpPort(wireMockConfig, getPort(wireMockConfig));
					driverServer = new WireMockServerMrChecker(wireMockConfig);
					
					driver = driverServer.getClient();
					
					try {
						driverServer.start();
					} catch (FatalStartupException e) {
						BFLogger.logError(e.getMessage() + "host " + getHost() + ":" + getPort(wireMockConfig));
						throw new FatalStartupException(e);
					}
				} else {
					driver = new WireMock(getHost(), getPort());
				}
				
				return new VirtualizedService(driver, driverServer, getHost(), getPort());
				
			}
			
			private String getHost() {
				if (null == this.httpHost) {
					httpHost = RuntimeParameters.MOCK_HTTP_HOST.getValue();
				}
				return httpHost;
			}
			
			private int getPort() {
				if (-1 == this.httpPort) {
					httpPort = RuntimeParameters.MOCK_HTTP_PORT.getValue()
							.isEmpty()
									? 80
									: getInteger(RuntimeParameters.MOCK_HTTP_PORT.getValue());
				}
				return httpPort;
			}
			
			private int getPort(WireMockConfiguration wireMockConfig) {
				if (-1 == this.httpPort) {
					httpPort = RuntimeParameters.MOCK_HTTP_PORT.getValue()
							.isEmpty()
									? wireMockConfig.dynamicPort()
											.portNumber()
									: getInteger(RuntimeParameters.MOCK_HTTP_PORT.getValue());
				}
				return httpPort;
			}
			
			private WireMockConfiguration setHttpPort(WireMockConfiguration wireMockConfig, int portHttp) {
				wireMockConfig.port(portHttp);
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
