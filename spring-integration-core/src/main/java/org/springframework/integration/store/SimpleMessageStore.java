/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.store;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.integration.util.UpperBound;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Map-based in-memory implementation of {@link MessageStore} and {@link MessageGroupStore}.
 * Enforces a maximum capacity for the store.
 *
 * @author Iwein Fuld
 * @author Mark Fisher
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Ryan Barker
 * @author Artem Bilan
 *
 * @since 2.0
 */
public class SimpleMessageStore extends AbstractMessageGroupStore
		implements MessageStore, ChannelMessageStore {

	private final ConcurrentMap<UUID, Message<?>> idToMessage = new ConcurrentHashMap<UUID, Message<?>>();

	private final ConcurrentMap<Object, SimpleMessageGroup> groupIdToMessageGroup =
			new ConcurrentHashMap<Object, SimpleMessageGroup>();

	private final ConcurrentMap<Object, UpperBound> groupToUpperBound = new ConcurrentHashMap<Object, UpperBound>();

	private final int groupCapacity;

	private final UpperBound individualUpperBound;

	private volatile LockRegistry lockRegistry;

	private volatile boolean isUsed;

	private volatile boolean copyOnGet = false;

	private final long upperBoundTimeout;

	/**
	 * Creates a SimpleMessageStore with a maximum size limited by the given capacity, or unlimited size if the given
	 * capacity is less than 1. The capacities are applied independently to messages stored via
	 * {@link #addMessage(Message)} and to those stored via {@link #addMessageToGroup(Object, Message)}. In both cases
	 * the capacity applies to the number of messages that can be stored, and once that limit is reached attempting to
	 * store another will result in an exception.
	 * @param individualCapacity The message capacity.
	 * @param groupCapacity      The capacity of each group.
	 */
	public SimpleMessageStore(int individualCapacity, int groupCapacity) {
		this(individualCapacity, groupCapacity, new DefaultLockRegistry());
	}

	/**
	 * Creates a SimpleMessageStore with a maximum size limited by the given capacity and the timeout in millisecond
	 * to wait for the empty slot in the store.
	 * @param individualCapacity The message capacity.
	 * @param groupCapacity      The capacity of each group.
	 * @param upperBoundTimeout  The time to wait if the store is at max capacity.
	 * @see #SimpleMessageStore(int, int)
	 * @since 3.0.8
	 */
	public SimpleMessageStore(int individualCapacity, int groupCapacity, long upperBoundTimeout) {
		this(individualCapacity, groupCapacity, upperBoundTimeout, new DefaultLockRegistry());
	}

	/**
	 * Creates a SimpleMessageStore with a maximum size limited by the given capacity and LockRegistry
	 * for the message group operations concurrency.
	 * @param individualCapacity The message capacity.
	 * @param groupCapacity      The capacity of each group.
	 * @param lockRegistry       The lock registry.
	 * @see #SimpleMessageStore(int, int, long, LockRegistry)
	 */
	public SimpleMessageStore(int individualCapacity, int groupCapacity, LockRegistry lockRegistry) {
		this(individualCapacity, groupCapacity, 0, lockRegistry);
	}


	/**
	 * Creates a SimpleMessageStore with a maximum size limited by the given capacity,
	 * the timeout in millisecond to wait for the empty slot in the store and LockRegistry
	 * for the message group operations concurrency.
	 * @param individualCapacity The message capacity.
	 * @param groupCapacity      The capacity of each group.
	 * @param upperBoundTimeout  The time to wait if the store is at max capacity
	 * @param lockRegistry       The lock registry.
	 * @since 3.0.8
	 */
	public SimpleMessageStore(int individualCapacity, int groupCapacity, long upperBoundTimeout,
	                          LockRegistry lockRegistry) {
		Assert.notNull(lockRegistry, "The LockRegistry cannot be null");
		this.individualUpperBound = new UpperBound(individualCapacity);
		this.groupCapacity = groupCapacity;
		this.lockRegistry = lockRegistry;
		this.upperBoundTimeout = upperBoundTimeout;
	}

	/**
	 * Creates a SimpleMessageStore with the same capacity for individual and grouped messages.
	 * @param capacity The capacity.
	 */
	public SimpleMessageStore(int capacity) {
		this(capacity, capacity);
	}

	/**
	 * Creates a SimpleMessageStore with unlimited capacity
	 */
	public SimpleMessageStore() {
		this(0);
	}

	/**
	 * Set to false to disable copying the group in {@link #getMessageGroup(Object)}.
	 * Starting with 4.1, this is false by default.
	 * @param copyOnGet True to copy, false to not.
	 * @since 4.0.1
	 */
	public void setCopyOnGet(boolean copyOnGet) {
		this.copyOnGet = copyOnGet;
	}

	public void setLockRegistry(LockRegistry lockRegistry) {
		Assert.notNull(lockRegistry, "The LockRegistry cannot be null");
		Assert.isTrue(!(this.isUsed), "Cannot change the lock registry after the store has been used");
		this.lockRegistry = lockRegistry;
	}

	@Override
	@ManagedAttribute
	public long getMessageCount() {
		return idToMessage.size();
	}

	@Override
	public <T> Message<T> addMessage(Message<T> message) {
		this.isUsed = true;
		if (!individualUpperBound.tryAcquire(this.upperBoundTimeout)) {
			throw new MessagingException(this.getClass().getSimpleName()
					+ " was out of capacity at, try constructing it with a larger capacity.");
		}
		this.idToMessage.put(message.getHeaders().getId(), message);
		return message;
	}

	@Override
	public Message<?> getMessage(UUID key) {
		return (key != null) ? this.idToMessage.get(key) : null;
	}

	@Override
	public Message<?> removeMessage(UUID key) {
		if (key != null) {
			Message<?> message = this.idToMessage.remove(key);
			if (message != null) {
				this.individualUpperBound.release();
			}
			return message;
		}
		else {
			return null;
		}
	}

	@Override
	public MessageGroup getMessageGroup(Object groupId) {
		Assert.notNull(groupId, "'groupId' must not be null");

		SimpleMessageGroup group = groupIdToMessageGroup.get(groupId);
		if (group == null) {
			return new SimpleMessageGroup(groupId, true);
		}
		if (this.copyOnGet) {
			return copy(group);
		}
		else {
			return group;
		}
	}

	@Override
	protected MessageGroup copy(MessageGroup group) {
		Lock lock = this.lockRegistry.obtain(group.getGroupId());
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup simpleMessageGroup = new SimpleMessageGroup(group, true);
				simpleMessageGroup.setLastModified(group.getLastModified());
				return simpleMessageGroup;
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			boolean unlocked = false;
			try {
				UpperBound upperBound;
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				if (group == null) {
					group = new SimpleMessageGroup(groupId, true);
					this.groupIdToMessageGroup.putIfAbsent(groupId, group);
					upperBound = new UpperBound(this.groupCapacity);
					upperBound.tryAcquire(-1);
					this.groupToUpperBound.putIfAbsent(groupId, upperBound);
				}
				else {
					upperBound = this.groupToUpperBound.get(groupId);
					Assert.state(upperBound != null, "'upperBound' must not be null.");
					lock.unlock();
					if (!upperBound.tryAcquire(this.upperBoundTimeout)) {
						unlocked = true;
						throw new MessagingException(this.getClass().getSimpleName()
								+ " was out of capacity at for individual group, " +
								"try constructing it with a larger capacity.");
					}
					lock.lockInterruptibly();
				}
				group.add(message);
				group.setLastModified(System.currentTimeMillis());
				return group;
			}
			finally {
				if (!unlocked) {
					lock.unlock();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public void removeMessageGroup(Object groupId) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup messageGroup = this.groupIdToMessageGroup.remove(groupId);
				if (messageGroup != null) {
					UpperBound upperBound = this.groupToUpperBound.remove(groupId);
					Assert.state(upperBound != null, "'upperBound' must not be null.");
					upperBound.release(this.groupCapacity);
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public MessageGroup removeMessageFromGroup(Object groupId, Message<?> messageToRemove) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				Assert.notNull(group, "MessageGroup for groupId '" + groupId + "' " +
						"can not be located while attempting to remove Message from the MessageGroup");
				if (group.remove(messageToRemove)) {
					UpperBound upperBound = this.groupToUpperBound.get(groupId);
					Assert.state(upperBound != null, "'upperBound' must not be null.");
					upperBound.release();
					group.setLastModified(System.currentTimeMillis());
				}
				return group;
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public void removeMessagesFromGroup(Object groupId, Collection<Message<?>> messages) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				Assert.notNull(group, "MessageGroup for groupId '" + groupId + "' " +
						"can not be located while attempting to remove Message(s) from the MessageGroup");
				UpperBound upperBound = this.groupToUpperBound.get(groupId);
				Assert.state(upperBound != null, "'upperBound' must not be null.");
				boolean modified = false;
				for (Message<?> messageToRemove : messages) {
					if (group.remove(messageToRemove)) {
						upperBound.release();
						modified = true;
					}
				}
				if (modified) {
					group.setLastModified(System.currentTimeMillis());
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public Iterator<MessageGroup> iterator() {
		return new HashSet<MessageGroup>(groupIdToMessageGroup.values()).iterator();
	}

	@Override
	public void setLastReleasedSequenceNumberForGroup(Object groupId, int sequenceNumber) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				Assert.notNull(group, "MessageGroup for groupId '" + groupId + "' " +
						"can not be located while attempting to set 'lastReleasedSequenceNumber'");
				group.setLastReleasedMessageSequenceNumber(sequenceNumber);
				group.setLastModified(System.currentTimeMillis());
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public void completeGroup(Object groupId) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				Assert.notNull(group, "MessageGroup for groupId '" + groupId + "' " +
						"can not be located while attempting to complete the MessageGroup");
				group.complete();
				group.setLastModified(System.currentTimeMillis());
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

	@Override
	public Message<?> pollMessageFromGroup(Object groupId) {
		Collection<Message<?>> messageList = this.getMessageGroup(groupId).getMessages();
		Message<?> message = null;
		if (!CollectionUtils.isEmpty(messageList)) {
			message = messageList.iterator().next();
			if (message != null) {
				this.removeMessagesFromGroup(groupId, message);
			}
		}
		return message;
	}

	@Override
	public int messageGroupSize(Object groupId) {
		return this.getMessageGroup(groupId).size();
	}

	@Override
	public MessageGroupMetadata getGroupMetadata(Object groupId) {
		return new MessageGroupMetadata(this.getMessageGroup(groupId));
	}

	@Override
	public Message<?> getOneMessageFromGroup(Object groupId) {
		return this.getMessageGroup(groupId).getOne();
	}

	public void clearMessageGroup(Object groupId) {
		Lock lock = this.lockRegistry.obtain(groupId);
		try {
			lock.lockInterruptibly();
			try {
				SimpleMessageGroup group = this.groupIdToMessageGroup.get(groupId);
				Assert.notNull(group, "MessageGroup for groupId '" + groupId + "' " +
						"can not be located while attempting to complete the MessageGroup");
				group.clear();
				group.setLastModified(System.currentTimeMillis());
				UpperBound upperBound = this.groupToUpperBound.get(groupId);
				Assert.state(upperBound != null, "'upperBound' must not be null.");
				upperBound.release(this.groupCapacity);
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MessagingException("Interrupted while obtaining lock", e);
		}
	}

}
