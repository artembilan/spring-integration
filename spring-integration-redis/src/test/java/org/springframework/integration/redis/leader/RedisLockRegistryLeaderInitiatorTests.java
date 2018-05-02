/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.redis.leader;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.integration.redis.rules.RedisAvailable;
import org.springframework.integration.redis.rules.RedisAvailableTests;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;

/**
 * @author Artem Bilan
 * @author Gary Russell
 * @author Glenn Renfro
 *
 * @since 4.3.9
 */
public class RedisLockRegistryLeaderInitiatorTests extends RedisAvailableTests {

	@Test
	@RedisAvailable
	public void testDistributedLeaderElection() throws Exception {
		RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), "LeaderInitiator");
		registry.expireUnusedOlderThan(-1);
		CountDownLatch granted = new CountDownLatch(1);
		CountingPublisher countingPublisher = new CountingPublisher(granted);
		List<LockRegistryLeaderInitiator> initiators = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			LockRegistryLeaderInitiator initiator =
					new LockRegistryLeaderInitiator(registry, new DefaultCandidate("foo:" + i, "bar"));
			initiator.setLeaderEventPublisher(countingPublisher);
			initiators.add(initiator);
		}

		for (LockRegistryLeaderInitiator initiator : initiators) {
			initiator.start();
		}

		assertThat(granted.await(10, TimeUnit.SECONDS), is(true));

		LockRegistryLeaderInitiator initiator1 = countingPublisher.initiator;

		LockRegistryLeaderInitiator initiator2 = null;

		for (LockRegistryLeaderInitiator initiator : initiators) {
			if (initiator != initiator1) {
				initiator2 = initiator;
				break;
			}
		}

		assertNotNull(initiator2);

		assertThat(initiator1.getContext().isLeader(), is(true));
		assertThat(initiator2.getContext().isLeader(), is(false));

		final CountDownLatch granted1 = new CountDownLatch(1);
		final CountDownLatch granted2 = new CountDownLatch(1);
		CountDownLatch revoked1 = new CountDownLatch(1);
		CountDownLatch revoked2 = new CountDownLatch(1);
		CountDownLatch acquireLockFailed1 = new CountDownLatch(1);
		CountDownLatch acquireLockFailed2 = new CountDownLatch(1);

		initiator1.setLeaderEventPublisher(new CountingPublisher(granted1, revoked1, acquireLockFailed1) {

			@Override
			public void publishOnRevoked(Object source, Context context, String role) {
				try {
					// It's difficult to see round-robin election, so block one initiator until the second is elected.
					assertThat(granted2.await(10, TimeUnit.SECONDS), is(true));
				}
				catch (InterruptedException e) {
					// No op
				}
				super.publishOnRevoked(source, context, role);
			}

		});

		initiator2.setLeaderEventPublisher(new CountingPublisher(granted2, revoked2, acquireLockFailed2) {

			@Override
			public void publishOnRevoked(Object source, Context context, String role) {
				try {
					// It's difficult to see round-robin election, so block one initiator until the second is elected.
					assertThat(granted1.await(10, TimeUnit.SECONDS), is(true));
				}
				catch (InterruptedException e) {
					// No op
				}
				super.publishOnRevoked(source, context, role);
			}

		});

		initiator1.getContext().yield();

		assertThat(revoked1.await(10, TimeUnit.SECONDS), is(true));

		assertThat(initiator2.getContext().isLeader(), is(true));
		assertThat(initiator1.getContext().isLeader(), is(false));

		initiator2.getContext().yield();

		assertThat(revoked2.await(10, TimeUnit.SECONDS), is(true));

		assertThat(initiator1.getContext().isLeader(), is(true));
		assertThat(initiator2.getContext().isLeader(), is(false));

		initiator2.stop();

		CountDownLatch revoked11 = new CountDownLatch(1);
		initiator1.setLeaderEventPublisher(new CountingPublisher(new CountDownLatch(1), revoked11, new CountDownLatch(1)));

		initiator1.getContext().yield();

		assertThat(revoked11.await(10, TimeUnit.SECONDS), is(true));
		assertThat(initiator1.getContext().isLeader(), is(false));

		initiator1.stop();
	}

	private static class CountingPublisher implements LeaderEventPublisher {

		private CountDownLatch granted;

		private CountDownLatch revoked;

		private volatile LockRegistryLeaderInitiator initiator;

		private final CountDownLatch acquireLockFailed;

		CountingPublisher(CountDownLatch granted, CountDownLatch revoked, CountDownLatch acquireLockFailed) {
			this.granted = granted;
			this.revoked = revoked;
			this.acquireLockFailed = acquireLockFailed;
		}

		CountingPublisher(CountDownLatch granted) {
			this(granted, new CountDownLatch(1), new CountDownLatch(1));
		}

		@Override
		public void publishOnRevoked(Object source, Context context, String role) {
			this.revoked.countDown();
		}

		@Override
		public void publishOnFailedToAcquire(Object source, Context context, String role) {
			this.acquireLockFailed.countDown();
		}

		@Override
		public void publishOnGranted(Object source, Context context, String role) {
			this.initiator = (LockRegistryLeaderInitiator) source;
			this.granted.countDown();
		}

	}

}
