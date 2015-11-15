import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.activation.DataHandler;

import org.apache.commons.io.IOUtils;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SActionState;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SSerializerPluginConfiguration;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.BimServerClientFactory;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.utils.FileDataSource;

/**
 * Unit test for simple App.
 */
public class AppTest{
	private BimServerClientFactory factory;

	public static void main(String [] args) {
		if (args.length!=2)
		{
			System.out.println("Usage: OsmSerializerTest.jar inputFile outputFile");
		} else{
			AppTest app = new AppTest();
			String downloadedOSM = app.checkInAndDownload(args[0]);
			app.saveAsOSM(args[1], downloadedOSM);
		}
	}

	private String checkInAndDownload(String filePath) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			// Create a new BimServerClient with authentication
			BimServerClientInterface bimServerClient = getFactory()
					.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));

			// Create a new project
			SProject newProject = bimServerClient.getBimsie1ServiceInterface().addProject("test" + Math.random());

			// This is the file we will be checking in
			File ifcFile = new File(filePath);

			// Find a deserializer to use
			SDeserializerPluginConfiguration deserializer = bimServerClient.getBimsie1ServiceInterface()
					.getSuggestedDeserializerForExtension("ifc");

			// Checkin
			Long progressId = -1L;

			progressId = bimServerClient.getBimsie1ServiceInterface().checkin(newProject.getOid(), "test",
					deserializer.getOid(), ifcFile.length(), ifcFile.getName(),
					new DataHandler(new FileDataSource(ifcFile)), true);

			// Get the status
			SLongActionState longActionState = bimServerClient.getRegistry().getProgress(progressId);
			if (longActionState.getState() == SActionState.FINISHED) {
				// Find a serializer
				SSerializerPluginConfiguration serializer = bimServerClient.getBimsie1ServiceInterface()
						.getSerializerByContentType("application/osm");

				// Get the project details
				newProject = bimServerClient.getBimsie1ServiceInterface().getProjectByPoid(newProject.getOid());

				// Download the latest revision (the one we just checked in)

				Long downloadId = bimServerClient.getBimsie1ServiceInterface().download(newProject.getLastRevisionId(),
						serializer.getOid(), true, true);
				SLongActionState downloadState = bimServerClient.getRegistry().getProgress(downloadId);
				if (downloadState.getState() == SActionState.FINISHED) {
					InputStream inputStream = bimServerClient.getBimsie1ServiceInterface().getDownloadData(downloadId)
							.getFile().getInputStream();
					IOUtils.copy(inputStream, baos);
					System.out.println(baos.size() + " bytes downloaded");
				}
			} else {
				System.out.println(longActionState.getState());
			}

			bimServerClient.getBimsie1ServiceInterface().deleteProject(newProject.getOid());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return baos.toString();
	}

	private BimServerClientFactory getFactory() {
		if (factory == null) {
			factory = new JsonBimServerClientFactory("http://localhost:8082");
		}
		return factory;
	}

	private String extractTrueOSM(String filePath) {

		try {
			byte[] encoded = Files.readAllBytes(Paths.get(filePath));
			return new String(encoded, Charset.defaultCharset());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	private void saveAsOSM(String filePath, String OSM) {
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(filePath));
			out.write(OSM);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (out != null)
					out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
