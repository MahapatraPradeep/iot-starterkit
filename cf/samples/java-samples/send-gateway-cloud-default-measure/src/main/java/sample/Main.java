package sample;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import commons.AbstractSample;
import commons.api.CoreService;
import commons.api.GatewayCloud;
import commons.api.GatewayCloudHttp;
import commons.api.GatewayCloudMqtt;
import commons.model.Authentication;
import commons.model.Device;
import commons.model.Gateway;
import commons.model.GatewayType;
import commons.model.gateway.Measure;
import commons.utils.Console;
import commons.utils.Constants;
import commons.utils.ObjectFactory;
import commons.utils.SecurityUtil;

/**
 * Main entry point of the sample application
 */
public class Main
extends AbstractSample {

	private GatewayCloud gatewayCloud;

	public static void main(String[] args) {
		new Main().execute();
	}

	@Override
	protected String getDescription() {
		return "Send default device measures";
	}

	@Override
	protected void promptProperties() {
		Console console = Console.getInstance();

		String host = properties.getProperty(Constants.IOT_HOST);
		host = console.awaitNextLine(host, "Hostname (e.g. 'test.cp.iot.sap'): ");
		properties.setProperty(Constants.IOT_HOST, host);

		String user = properties.getProperty(Constants.IOT_USER);
		user = console.awaitNextLine(user, "Username (e.g. 'root#0'): ");
		properties.setProperty(Constants.IOT_USER, user);

		String gatewayType = properties.getProperty(Constants.GATEWAY_TYPE);
		gatewayType = console.awaitNextLine(gatewayType, "Gateway Type ('rest' or 'mqtt'): ");
		properties.setProperty(Constants.GATEWAY_TYPE,
			GatewayType.fromValue(gatewayType).getValue());

		String physicalAddress = properties.getProperty(Constants.DEVICE_ID);
		physicalAddress = console.awaitNextLine(physicalAddress, "Device ID (e.g. '100'): ");
		properties.setProperty(Constants.DEVICE_ID, physicalAddress);

		String password = properties.getProperty(Constants.IOT_PASSWORD);
		password = console.nextPassword("Password for your username: ");
		properties.setProperty(Constants.IOT_PASSWORD, password);

		console.close();
	}

	@Override
	protected void execute() {
		String host = properties.getProperty(Constants.IOT_HOST);
		String user = properties.getProperty(Constants.IOT_USER);
		String password = properties.getProperty(Constants.IOT_PASSWORD);
		String deviceId = properties.getProperty(Constants.DEVICE_ID);
		GatewayType gatewayType = GatewayType
			.fromValue(properties.getProperty(Constants.GATEWAY_TYPE));

		CoreService coreService = new CoreService(host, user, password);

		try {
			System.out.println(Constants.SEPARATOR);
			Gateway gateway = coreService.getOnlineGateway(gatewayType);

			System.out.println(Constants.SEPARATOR);
			Device device = coreService.getOrAddDevice(deviceId, gateway);

			System.out.println(Constants.SEPARATOR);
			Authentication authentication = coreService.getAuthentication(device);
			SSLSocketFactory sslSocketFactory = SecurityUtil.getSSLSocketFactory(device,
				authentication);

			System.out.println(Constants.SEPARATOR);
			gatewayCloud = GatewayType.REST.equals(gatewayType)
				? new GatewayCloudHttp(device, sslSocketFactory)
				: new GatewayCloudMqtt(device, sslSocketFactory);

			sendMeasures();
		}
		catch (IOException | GeneralSecurityException | IllegalStateException e) {
			System.err.println(String.format("[ERROR] Execution failure: %1$s", e.getMessage()));
			System.exit(1);
		}
	}

	/**
	 * Sends random temperature measures on behalf of the device to the Gateway Cloud. Temperature
	 * measure is being sent each second during the 5 seconds time frame.
	 */
	private void sendMeasures()
	throws IOException {
		String host = properties.getProperty(Constants.IOT_HOST);

		try {
			gatewayCloud.connect(host);
		}
		catch (IOException e) {
			throw new IOException("Unable to connect to the Gateway Cloud", e);
		}

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				Measure measure = ObjectFactory.buildTemperatureMeasure();

				try {
					gatewayCloud.send(measure, Measure.class);
				}
				catch (IOException e) {
					// do nothing
				}
				finally {
					System.out.println(Constants.SEPARATOR);
				}
			}

		}, 0l, 1000, TimeUnit.MILLISECONDS);

		try {
			executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			throw new IOException("Interrupted exception", e);
		}
		finally {
			gatewayCloud.disconnect();
			executor.shutdown();
		}
	}

}