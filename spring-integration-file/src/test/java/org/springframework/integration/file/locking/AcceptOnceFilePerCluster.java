package org.springframework.integration.file.locking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Artem Bilan
 */
public class AcceptOnceFilePerCluster {

	public static void main(String[] args) throws IOException {
		for (int i = 0; i < 4; i++) {
			cloneJVM(AcceptOnceNioFileLocking.class.getName());
		}

		System.out.println("Waiting for shutdown");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
		bufferedReader.readLine();
		System.exit(1);
	}

	public static void cloneJVM(String className) {
		String cp = System.getProperty("java.class.path");
		String home = System.getProperty("java.home");

		Process proc = null;
		String java = home + "/bin/java".replace("\\", "/");

		String[] cmdArray = {java, "-cp", cp, className};
		try {
			proc = Runtime.getRuntime().exec(cmdArray);
		}
		catch (IOException ioe) {
			throw new IllegalStateException("Cannot start command " + cmdArray, ioe);
		}

		System.out.println("Started fork");
		final Process p = proc;

		final BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		final BufferedReader ebr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		final AtomicBoolean run = new AtomicBoolean(true);

		Thread reader = copyStdXxx(br, run, System.out);
		Thread errReader = copyStdXxx(ebr, run, System.err);

		reader.start();
		errReader.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Stopping fork...");
				run.set(false);
				if (p != null)
					p.destroy();

				try {
					p.waitFor();
				}
				catch (InterruptedException e) {
					// ignore
				}
				System.out.println("Fork stopped");
			}
		});
	}

	private static Thread copyStdXxx(final BufferedReader br,
									 final AtomicBoolean run, final PrintStream out) {
		Thread reader = new Thread(new Runnable() {

			public void run() {
				try {
					String line = null;
					do {
						while ((line = br.readLine()) != null) {
							out.println("[FORK] " + line);
						}
					} while (run.get());
				}
				catch (Exception ex) {
					// ignore and exit
				}
			}
		});
		return reader;
	}

}
