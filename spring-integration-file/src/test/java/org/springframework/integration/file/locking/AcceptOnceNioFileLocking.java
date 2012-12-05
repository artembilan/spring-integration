package org.springframework.integration.file.locking;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Artem Bilan
 */
public class AcceptOnceNioFileLocking {

	public static String JVM_PROCESS_ID;

	static {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		int index = name.indexOf("@");
		JVM_PROCESS_ID = name.substring(0, index);
	}

	public static void main(String[] args) {
		new ClassPathXmlApplicationContext("AcceptOnceNioFileLocking-context.xml", AcceptOnceNioFileLocking.class);
	}

	public static void readAndLogFile(File lockedFile) throws Exception {
		Field channelCacheField = FileChannelCache.class.getDeclaredField("channelCache");
		channelCacheField.setAccessible(true);
		Map<File, FileChannel> channelCache = (Map<File, FileChannel>) channelCacheField.get(null);
		FileChannel fileChannel = channelCache.get(lockedFile);
		ByteBuffer byteBuffer = ByteBuffer.allocate((int) fileChannel.size());
		fileChannel.read(byteBuffer);
		System.out.println("Read File " + lockedFile.getName() + " from process " + JVM_PROCESS_ID + " with content: " + new String(byteBuffer.array()));
	}

}
