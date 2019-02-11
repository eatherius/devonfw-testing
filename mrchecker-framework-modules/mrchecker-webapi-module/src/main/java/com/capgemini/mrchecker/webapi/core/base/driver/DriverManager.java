package com.capgemini.mrchecker.webapi.core.base.driver;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;

import com.capgemini.mrchecker.test.core.logger.BFLogger;
import com.capgemini.mrchecker.webapi.core.base.properties.PropertiesFileSettings;
import com.capgemini.mrchecker.webapi.core.base.runtime.RuntimeParameters;
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
	
	private static ThreadLocal<WireMock> driversVirtualServer = new ThreadLocal<WireMock>();
	
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
		driversVirtualServer.remove();
	}
	
	public static WireMock getDriverVirtualService() {
		WireMock driver = driversVirtualServer.get();
		if (driver == null) {
			driver = createDriverVirtualServer();
			driversVirtualServer.set(driver);
			BFLogger.logDebug("driver:" + driver.toString());
		}
		return driver;
	}
	
	public static RequestSpecification getDriverWebAPI() {
		RequestSpecification driver = createDriverWebAPI();
		BFLogger.logDebug("driver:" + driver.toString());
		return driver;
	}
	
	public static void closeDriverVirtualServer() {
		WireMock driverVirtualServer = driversVirtualServer.get();
		if (driverVirtualServer == null) {
			BFLogger.logDebug("closeDriverVirtualServer() was called but there was no driver for this thread.");
		} else {
			try {
				BFLogger.logDebug(
						"Closing communication to Mock Server under: " + driverVirtualServer.toString() + ":" + driverVirtualServer.port() + " https://localhost:" + driverVirtualServer.httpsPort());
				driverVirtualServer.stop();
			} catch (Exception e) {
				BFLogger.logDebug("Ooops! Something went wrong while closing the driver");
				e.printStackTrace();
			} finally {
				driverVirtualServer = null;
				driversVirtualServer.remove();
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
	
	static WireMock createDriverVirtualServer() {
		BFLogger.logDebug("Creating new Mock Server");
		
		WireMock driver = Driver.WIREMOCK.getDriver();
		
		BFLogger.logDebug("Mock server running under http://localhost:" + driver.port() + " https://localhost:" + driver.httpsPort());
		return driver;
	}
	
	private enum Driver {
		
		WIREMOCK {
			
			private WireMockConfiguration wireMockConfig = wireMockConfig().extensions(new BodyTransformer());
			
			public WireMock getDriver() throws FatalStartupException {
				
				int portHttp = RuntimeParameters.MOCK_HTTP_PORT.getValue()
						.isEmpty() ? 0 : getInteger(RuntimeParameters.MOCK_HTTP_PORT.getValue());
				
				int portHttps = RuntimeParameters.MOCK_HTTPS_PORT.getValue()
						.isEmpty() ? 0 : getInteger(RuntimeParameters.MOCK_HTTPS_PORT.getValue());
				
				String hostHttp = RuntimeParameters.MOCK_HTTP_HOST.getValue();
				
				setHttpHost(hostHttp);
				setHttpPort(portHttp);
				// setHttpsPort(portHttps);
				
				WireMock driver = new WireMock(wireMockConfig);
				
				try {
					driver.start();
				} catch (FatalStartupException e) {
					BFLogger.logError(e.getMessage() + " http_port=" + RuntimeParameters.MOCK_HTTP_PORT.getValue() + " https_port=" + RuntimeParameters.MOCK_HTTPS_PORT.getValue());
					throw new FatalStartupException(e);
				}
				return driver;
				
			}
			
			private void setHttpHost(String hostHttp) {
				if ("http://localhost" != hostHttp) {
					this.wireMockConfig.bindAddress(RuntimeParameters.MOCK_HTTP_HOST.getValue());
				}
			}
			
			private void setHttpPort(int portHttp) {
				if (0 != portHttp) {
					this.wireMockConfig.port(portHttp);
				} else {
					this.wireMockConfig.dynamicPort();
				}
			}
			
			private void setHttpsPort(int portHttps) {
				if (0 != portHttps) {
					this.wireMockConfig.httpsPort(portHttps);
				} else {
					this.wireMockConfig.dynamicHttpsPort();
				}
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
		
		public WireMock getDriver() {
			return null;
		}
		
	}
}
