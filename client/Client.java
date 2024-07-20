import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
	private String hostname = "localhost";
	private int port = 9487; // Port number must match the server
	// A set of valid commands this client can execute
	private static final Set<String> validCommands = new HashSet<>(Arrays.asList("list", "put"));

	// Method to send a "list" command to the server
	public void listFiles() {
		try (Socket socket = new Socket(hostname, port)) { // Establish connection with the server
			PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			output.println("list"); // Send "list" command to the server

			String response;
			while ((response = input.readLine()) != null) {
				if ("END".equals(response))
					break; // Break on "END" message
				System.out.println(response); // Print each response line from the server
			}
		} catch (UnknownHostException ex) {
			System.err.println("Server not found: " + ex.getMessage());
		} catch (IOException ex) {
			System.err.println("I/O error: " + ex.getMessage());
		}
	}

	// Method to put (upload) a file to the server
	public void put(String fname) {
		File file = new File(fname);
		// Check if the specified file exists on the local system
		if (!file.exists()) {
			System.out.println("Error: Cannot open local file '" + fname + "' for reading.");
			return;
		}

		// Check if the file size exceeds 64KB
		long fileSize = file.length();
		if (fileSize > 65536) {
			System.out.println("Error: File size exceeds the 64KB limit.");
			return;
		}

		// Start the file upload process
		try (Socket socket = new Socket(hostname, port);
				FileInputStream fileIn = new FileInputStream(file);
				OutputStream out = socket.getOutputStream();
				PrintWriter pw = new PrintWriter(out, true);
				BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

			pw.println("put " + file.getName()); // Send the "put" command with the file name to the server

			// Read the file and send it to the server in chunks of 4096 bytes
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = fileIn.read(buffer)) > 0) {
				out.write(buffer, 0, bytesRead);
			}
			socket.shutdownOutput(); // Important to signal the end of file data

			String response = input.readLine();
			if (response.startsWith("Error:")) {
				System.out.println(response); // Output the error message from the server directly
			} else {
				System.out.println("Uploaded file " + fname + ".");
			}

		} catch (UnknownHostException ex) {
			System.err.println("Server not found: " + ex.getMessage());
		} catch (IOException ex) {
			System.err.println("I/O error: " + ex.getMessage());
		}
	}

	// The main method to parse command line arguments and execute the corresponding
	// command
	public static void main(String[] args) {
		Client client = new Client();
		// Check if at least one command-line argument is provided
		if (args.length > 0) {
			switch (args[0]) {
				case "put":
					if (args.length > 1) { // Handle the "put" command
						client.put(args[1]); // Take the second argument as the filename for put operation
					} else {
						System.err.println("Error: 'put' command requires a filename argument.");
					}
					break;
				case "list":
					if (args.length == 1) {
						client.listFiles(); // Handle the "list" command
					} else {
						System.err.println("Error: 'list' command does not take additional arguments.");
					}
					break;
				default:
					System.err.println("Invalid command. Valid commands are: " + validCommands);
					break;
			}
		} else {
			// Provide usage instructions if no arguments are given
			System.out.println("Usage: java Client <command> [filename]");
		}
	}
}