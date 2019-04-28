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
package io.reactivex.swt;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.eclipse.EclipseSchedulers;
import io.reactivex.swt.listener.SwtObservables;
import io.reactivex.swt.schedulers.SwtSchedulers;

public class App2 {
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private static Text text;
	private static Button button;

	private volatile static long count = 0;
	private static Observable<Long> timerObservable;
	private static void createTimer() {
		timerObservable = Observable.create(x -> {
			while(count >= 0) {
				x.onNext(++count);									
				Thread.sleep(10);
			}
			x.onComplete();
		});
		
		timerObservable = timerObservable
				.subscribeOn(EclipseSchedulers.create("Waiting..."))
				.observeOn(SwtSchedulers.defaultDisplayThread());
	}
	
	private static String getTimeStr() {
		return "time: " + (double)count/100 + " s\n";
	}
	
	private static GitHubApi gitHubApi = ApiService.getGitHubApi();
	private static Single<List<Contributor>> contributorsObservable;
	private static void createContributors() {
		// Get an observable to subscribe to
		contributorsObservable = gitHubApi.contributors("eclipse",
				"eclipse.platform.ui");

		// Do query for contributors on another thread and sync with the
		// SWT main thread.
		// Comment out the next lines and see the UI freezing by trying
		// to click the check button.
		contributorsObservable = contributorsObservable
				.subscribeOn(EclipseSchedulers.create("Fetching Contributors..."))
				.observeOn(SwtSchedulers.defaultDisplayThread());		
	}
	
	private static void observe() {
		Observable<SelectionEvent> widgetSelected = SwtObservables.widgetSelected(button);
		widgetSelected.subscribe(e -> {
			count = 0;
			
			// Show the contributors in the text field by subscribing or on
			// error do system print line
			contributorsObservable.subscribe(contributors -> {
				StringBuilder sb = new StringBuilder(getTimeStr());
				count = -1;
				for (Contributor contributor : contributors) {
					sb.append(contributor.getLogin());
					sb.append(" : ");
					sb.append(contributor.getContributions());
					sb.append(LINE_SEPARATOR);
				}
				text.setText(sb.toString());
			}, ex   -> { 
				ex.printStackTrace(); 
			});
			
			timerObservable.subscribe(
				time -> { if(count<=0) { return; } 
							text.setText(getTimeStr()); },
				ex   -> { ex.printStackTrace(); });
		});
	}
	
	public static void main(String[] args) {

		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setSize(600, 600);

		shell.setLayout(new GridLayout(2, false));

		button = new Button(shell, SWT.PUSH);
		button.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		button.setText("Load data");

		createTimer();
		createContributors();
		observe();

		// This button is just intended to prove UI freezes, if no
		// schedulers are applied
		Button responsiveButton = new Button(shell, SWT.CHECK);
		responsiveButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		responsiveButton.setText("I won't be responsive when querying on the main thread");

		text = new Text(shell, SWT.MULTI | SWT.LEAD | SWT.BORDER | SWT.READ_ONLY);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		text.setText("Press load data");

		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}
