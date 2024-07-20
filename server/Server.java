import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Server {

	private static final int PORT = 9487; // Port number
	private static final ReentrantLock lock = new ReentrantLock(); // For handling concurrent file writes

	public static void main(String[] args) {
		ExecutorService executor = null;
		ServerSocket serverSocket = null;

		// Create a new log file each time the server starts
		try {
			new PrintWriter("log.txt").close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return; // Exit if the log file cannot be created
		}

		try {
			serverSocket = new ServerSocket(PORT); // Initialize the ServerSocket
			System.out.println("Server is listening on port " + PORT);
			executor = Executors.newFixedThreadPool(20); // Initialize the ExecutorService

			while (true) {
				Socket clientSocket = serverSocket.accept(); // Accept new clients
				System.out.println("New client connected");
				executor.submit(new ClientHandler(clientSocket)); // Handle each client in a separate thread
			}
		} catch (IOException e) {
			System.err.println("Could not listen on port: " + PORT + ".");
			e.printStackTrace();
		} finally {
			if (serverSocket != null) {
				try {
					serverSocket.close(); // Close the server socket
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (executor != null) {
				executor.shutdown(); // Shutdown the executor service
			}
		}
	}

	private static class ClientHandler implements Runnable {
		private Socket socket;

		public ClientHandler(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

				String message = input.readLine();
				System.out.println("Message from client: " + message);
				String clientIP = socket.getInetAddress().getHostAddress(); // Get client IP address

				// Handle "list" command
				if ("list".equals(message)) {
					logRequest(clientIP, "list"); // Log the request
					File folder = new File("serverFiles"); // Adjust the path as necessary
					File[] listOfFiles = folder.listFiles((dir, name) -> name.endsWith(".txt"));

					if (listOfFiles != null) {
						output.println("Listing " + listOfFiles.length + " file(s):"); // Output the number of files
						for (File file : listOfFiles) {
							if (file.isFile()) {
								output.println(file.getName()); // Send each file name to the client
							}
						}
					} else {
						output.println("No files found.");
					}
				}
				// Handle "put" command
				else if (message.startsWith("put ")) {
					logRequest(clientIP, "put"); // Log the request
					String filename = message.substring(4).trim(); // Extract and trim filename
					// Ensure filename ends with ".txt"
					if (!filename.endsWith(".txt")) {
						output.println("Error: Only text files are allowed.");
						return;
					}
					File file = new File("serverFiles/" + filename);

					lock.lock(); // Lock to prevent concurrent writes
					try {
						if (file.exists()) {
							output.println("Error: Cannot upload file '" + filename + "'; already exists on server.");
						} else {
							saveFileFromClient(file, socket.getInputStream(), output);
						}
					} finally {
						lock.unlock(); // Ensure the lock is unlocked regardless of success or failure
					}
				} else {
					output.println("Error: Unsupported command");
				}
			} catch (IOException ex) {
				System.out.println("Server exception: " + ex.getMessage());
				ex.printStackTrace();
			} finally {
				try {
					socket.close(); // Ensure the socket is closed after handling
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void logRequest(String clientIP, String requestType) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'|'HH:mm:ss");
			String timestamp = dateFormat.format(new Date()); // Get current date and time
			String logMessage = String.format("%s|%s|%s%n", timestamp, clientIP, requestType);

			lock.lock(); // Use the existing lock to handle concurrent writes to the log file
			try {
				PrintWriter logWriter = new PrintWriter(new FileOutputStream("log.txt", true));
				logWriter.append(logMessage);
				logWriter.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				lock.unlock();
			}
		}

		private void saveFileFromClient(File file, InputStream clientInputStream, PrintWriter output) {
			// Temporary file output stream setup
			try (FileOutputStream fileOut = new FileOutputStream(file)) {
				byte[] buffer = new byte[4096]; // Buffer for reading data
				int bytesRead;
				long totalBytesRead = 0; // Track the total bytes read from the input stream

				while ((bytesRead = clientInputStream.read(buffer)) != -1) {
					totalBytesRead += bytesRead;
					// Check if file size exceeds 64Kb limit
					if (totalBytesRead > 65536) {
						file.delete(); // Delete the partially written file
						output.println("Error: File size exceeds the 64Kb limit.");
						return; // Exit the method to prevent further writing
					}
					fileOut.write(buffer, 0, bytesRead);
				}
				output.println("Uploaded file " + file.getName());
			} catch (IOException e) {
				// Attempt to clean up by deleting potentially partially written file
				file.delete();
				output.println("Error: Cannot save file.");
			}
		}
	}
}