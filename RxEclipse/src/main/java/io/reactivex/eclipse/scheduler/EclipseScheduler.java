/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.eclipse.scheduler;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * {@link Scheduler}, which can be used to syncronize with the SWT main thread.
 */
public final class EclipseScheduler extends Scheduler {

	private String jobName;

	public EclipseScheduler(String jobName) {
		this.jobName = jobName;
	}

	@Override
	public Disposable scheduleDirect(Runnable run, long delay, TimeUnit unit) {
		if (run == null)
			throw new NullPointerException("run == null");
		if (unit == null)
			throw new NullPointerException("unit == null");

		run = RxJavaPlugins.onSchedule(run);

		ScheduledRunnable scheduled = new ScheduledRunnable(run);

		executeRunnable(jobName, delay, unit, scheduled);

		return scheduled;
	}

	private static void executeRunnable(String title, long delay, TimeUnit unit, ScheduledRunnable scheduled) {
		Job job = Job.create(title, monitor -> {
			scheduled.run();
			return Status.OK_STATUS;
		});
		job.schedule(unit.toMillis(delay));
	}

	@Override
	public Worker createWorker() {
		return new EclipseWorker(jobName);
	}

	private static final class EclipseWorker extends Worker {

		private volatile boolean disposed;
		private String title;

		EclipseWorker(String title) {
			this.title = title;
		}

		@Override
		public Disposable schedule(Runnable run, long delay, TimeUnit unit) {
			if (run == null)
				throw new NullPointerException("run == null");
			if (unit == null)
				throw new NullPointerException("unit == null");

			if (disposed) {
				return Disposables.disposed();
			}

			run = RxJavaPlugins.onSchedule(run);

			ScheduledRunnable scheduled = new ScheduledRunnable(run);

			executeRunnable(title, delay, unit, scheduled);

			// Re-check disposed state for removing in case we were racing a
			// call to dispose().
			if (disposed) {
				return Disposables.disposed();
			}

			return scheduled;
		}

		@Override
		public void dispose() {
			disposed = true;
		}

		@Override
		public boolean isDisposed() {
			return disposed;
		}
	}

	private static final class ScheduledRunnable implements Runnable, Disposable {
		private final Runnable delegate;

		private volatile boolean disposed;

		ScheduledRunnable(Runnable delegate) {
			this.delegate = delegate;
		}

		@Override
		public void run() {
			try {
				delegate.run();
			} catch (Throwable t) {
				IllegalStateException ie = new IllegalStateException("Fatal Exception thrown on Scheduler.", t);
				RxJavaPlugins.onError(ie);
				Thread thread = Thread.currentThread();
				thread.getUncaughtExceptionHandler().uncaughtException(thread, ie);
			}
		}

		@Override
		public void dispose() {
			disposed = true;
		}

		@Override
		public boolean isDisposed() {
			return disposed;
		}
	}
}