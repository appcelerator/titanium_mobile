/* global danger, fail, warn, markdown, message */

// requires
const fs = require('fs-extra');
const path = require('path');
const packageJSON = require('./package.json');
const DOMParser = require('xmldom').DOMParser;
// Due to bug in danger, we hack env variables in build process.
const ENV = fs.existsSync('./env.json') ? require('./env.json') : process.env;
// constants
const github = danger.github;
// Currently used PR-labels
const Label = {
	NEEDS_JIRA: 'needs jira 🚨',
	NEEDS_TESTS: 'needs tests 🚨',
	NO_TESTS: 'no tests',
	IOS: 'ios',
	ANDROID: 'android',
	COMMUNITY: 'community 🔥',
	DOCS: 'docs 📔',
	MERGE_CONFLICTS: 'merge conflicts 🚨',
	IN_QE_TESTING: 'in-qe-testing 🕵'
};
// Sets of existing labels, labels we want to add/exist, labels we want to remove (if they exist)
const existingLabelNames = new Set(github.issue.labels.map(l => l.name));
const labelsToAdd = new Set();
const labelsToRemove = new Set();

async function checkStats(pr) {
	// Check if the user deleted more code than added, give a thumbs-up if so
	if (pr.deletions > pr.additions) {
		message(':thumbsup: Hey!, You deleted more code than you added. That\'s awesome!');
	}
	// TODO: Check for PRs above a certain threshold of changes and warn?
}

// Check npm test output
async function checkNPMTestOutput() {
	const exists = await fs.pathExists('./npm_test.log');
	if (!exists) {
		return;
	}
	const npmTestOutput = await fs.readFile('./npm_test.log', 'utf8');
	if (npmTestOutput.includes('Test failed.  See above for more details.')) {
		fail(':disappointed_relieved: `npm test` failed. See below for details.');
		message('```' + npmTestOutput + '\n```');
	}
}

// Check that we have a JIRA Link in the body
async function checkJIRA() {
	const body = github.pr.body;
	const hasJIRALink = body.match(/https:\/\/jira\.appcelerator\.org\/browse\/[A-Z]+-\d+/);
	if (!hasJIRALink) {
		labelsToAdd.add(Label.NEEDS_JIRA);
		warn('There is no linked JIRA ticket in the PR body. Please include the URL of the relevant JIRA ticket. If you need to, you may file a ticket on ' + danger.utils.href('https://jira.appcelerator.org/secure/CreateIssue!default.jspa', 'JIRA'));
	} else {
		labelsToRemove.add(Label.NEEDS_JIRA);
	}
}

// Check that package.json and package-lock.json stay in-sync
async function checkPackageJSONInSync() {
	const hasPackageChanges = danger.git.modified_files.indexOf('package.json') !== -1;
	const hasLockfileChanges = danger.git.modified_files.indexOf('package-lock.json') !== -1;
	if (hasPackageChanges && !hasLockfileChanges) {
		warn(':lock: Changes were made to package.json, but not to package-lock.json - <i>Perhaps you need to run `npm install`?</i>');
	}
}

// Check that if we modify the Android or iOS SDK, we also update the tests
// Also, assign labels based on changes to different dir paths
async function checkChangedFileLocations() {
	const modified = danger.git.modified_files.concat(danger.git.created_files);
	const modifiedAndroidFiles = modified.filter(p => p.startsWith('android/') && p.endsWith('.java'));
	const modifiedIOSFiles = modified.filter(p => {
		return p.startsWith('iphone/') && (p.endsWith('.h') || p.endsWith('.m'));
	});

	// Auto-assign android/ios labels
	if (modifiedAndroidFiles.length > 0) {
		labelsToAdd.add(Label.ANDROID);
	}
	if (modifiedIOSFiles.length > 0) {
		labelsToAdd.add(Label.IOS);
	}
	// Check if apidoc was modified and apply 'docs' label?
	const modifiedApiDocs = modified.filter(p => p.startsWith('apidoc/'));
	if (modifiedApiDocs.length > 0) {
		labelsToAdd.add(Label.DOCS);
	}
	// Mark hasAppChanges if 'common' dir is changed too!
	const modifiedCommonJSAPI = modified.filter(p => p.startsWith('common/'));

	// Check if any tests were changed/added
	const hasAppChanges = (modifiedAndroidFiles.length + modifiedIOSFiles.length + modifiedCommonJSAPI.length) > 0;
	const testChanges = modified.filter(p => p.startsWith('tests/') && p.endsWith('.js'));
	const hasTestChanges = testChanges.length > 0;
	const hasNoTestsLabel = existingLabelNames.has(Label.NO_TESTS);
	// If we changed android/iOS source, but didn't change tests and didn't use the 'no tests' label
	// fail the PR
	if (hasAppChanges && !hasTestChanges && !hasNoTestsLabel) {
		labelsToAdd.add(Label.NEEDS_TESTS);
		const testDocLink = github.utils.fileLinks([ 'README.md#unit-tests' ]);
		fail(`:microscope: There are library changes, but no changes to the unit tests. That's OK as long as you're refactoring existing code, but will require an admin to merge this PR. Please see ${testDocLink} for docs on unit testing.`); // eslint-disable-line max-len
	} else {
		// If it has the "needs tests" label, remove it
		labelsToRemove.add(Label.NEEDS_TESTS);
	}
}

// Does the PR have merge conflicts?
async function checkMergeable() {
	if (github.pr.mergeable_state === 'dirty') {
		labelsToAdd.add(Label.MERGE_CONFLICTS);
	} else {
		// assume it has no conflicts
		labelsToRemove.add(Label.MERGE_CONFLICTS);
	}
}

// Check PR author to see if it's community, etc
async function checkCommunity() {
	// Don't give special thanks to the greenkeeper bot account
	if (github.pr.user.login === 'greenkeeper[bot]') {
		return;
	}
	if (github.pr.author_association === 'FIRST_TIMER') {
		labelsToAdd.add(Label.COMMUNITY);
		// Thank them profusely! This is their first ever github commit!
		message(`:rocket: Wow, ${github.pr.user.login}, your first contribution to GitHub and it's to help us make Titanium better! You rock! :guitar:`);
	} else if (github.pr.author_association === 'FIRST_TIME_CONTRIBUTOR') {
		labelsToAdd.add(Label.COMMUNITY);
		// Thank them, this is their first contribution to this repo!
		message(`:confetti_ball: Welcome to the Titanium SDK community, ${github.pr.user.login}! Thank you so much for your PR, you're helping us make Titanium better. :gift:`);
	} else if (github.pr.author_association === 'CONTRIBUTOR') {
		labelsToAdd.add(Label.COMMUNITY);
		// Be nice, this is a community member who has landed PRs before!
		message(`:tada: Another contribution from our awesome community member, ${github.pr.user.login}! Thanks again for helping us make Titanium SDK better. :thumbsup:`);
	}
}

/**
 * Given the `labelsToAdd` Set, add any labels that aren't already on the PR.
 */
async function addMissingLabels() {
	const filteredLabels = [ ...labelsToAdd ].filter(l => !existingLabelNames.has(l));
	if (filteredLabels.length === 0) {
		return;
	}
	await github.api.issues.addLabels({ owner: github.pr.base.repo.owner.login, repo: github.pr.base.repo.name, number: github.pr.number, labels: filteredLabels });
}

async function requestReviews() {
	// someone already started reviewing this PR, move along...
	if (github.pr.review_comments !== 0) {
		return;
	}

	// Based on the labels, auto-assign review requests if there's been no review comments yet
	const existingReviewers = github.pr.requested_teams;
	console.log(`Existing review requests for this PR: ${JSON.stringify(existingReviewers)}`);
	// Now based on the labels, auto-assign reviewers!
	const teamSlugs = existingReviewers.map(t => t.slug);
	const teamsToReview = [];
	if (labelsToAdd.includes(Label.IOS)) {
		teamsToReview.push('appcelerator/ios');
	}
	if (labelsToAdd.includes(Label.ANDROID)) {
		teamsToReview.push('appcelerator/android');
	}
	if (labelsToAdd.includes(Label.DOCS)) {
		teamsToReview.push('appcelerator/docs');
	}
	// filter to the set of teams not already assigned to review (add only those missing)
	teamsToReview.filter(t => !teamSlugs.includes(t));
	console.log(`Assigning PR reviews to teams: ${teamsToReview}`);
	await github.api.pullRequests.createReviewRequest({ owner: github.pr.base.repo.owner.login, repo: github.pr.base.repo.name, number: github.pr.number, team_reviewers: teamsToReview });
}

// If a PR has a completed review that is approved, and does not have the in-qe-testing label, add it
async function checkPRisApproved() {
	if (github.pr.review_comments === 0) {
		return;
	}

	const reviews = github.reviews;
	const blockers = reviews.filter(r => r.state === 'CHANGES_REQUESTED' || r.state === 'PENDING');
	const good = reviews.filter(r => r.state === 'APPROVED' || r.state === 'DISMISSED');
	if (good.length > 0 && blockers.length === 0) {
		labelsToAdd.add(Label.IN_QE_TESTING);
	}
}

// Auto assign milestone based on version in package.json
async function updateMilestone() {
	if (github.pr.milestone) {
		return;
	}
	const expected_milestone = packageJSON.version;
	const milestones = await github.api.issues.getMilestones({ owner: github.pr.base.repo.owner.login, repo: github.pr.base.repo.name });
	const milestone_match = milestones.data.find(m => m.title === expected_milestone);
	if (!milestone_match) {
		console.log('Unable to find a Github milestone matching the version in package.json');
		return;
	}
	await github.api.issues.edit({ owner: github.pr.base.repo.owner.login, repo: github.pr.base.repo.name, number: github.pr.number, milestone: milestone_match.number });
}

/**
 * Removes the set of labels from an issue (if they already existed on it)
 */
async function removeLabels() {
	for (const label of labelsToRemove) {
		if (existingLabelNames.has(label)) {
			await github.api.issues.removeLabel({ owner: github.pr.base.repo.owner.login, repo: github.pr.base.repo.name, number: github.pr.number, name: label });
		}
	}
}

// Check for iOS crash file
async function checkForIOSCrash() {
	const files = await fs.readdir(__dirname);
	const crashFiles = files.filter(p => p.startsWith('mocha_') && p.endsWith('.crash'));
	if (crashFiles.length > 0) {
		const crashLink = danger.utils.href(`${ENV.BUILD_URL}artifact/${crashFiles[0]}`, 'the crash log');
		fail(`Test suite crashed on iOS simulator. Please see ${crashLink} for more details.`);
	}
}

// Report test failures
async function gatherFailedTestcases(reportPath) {
	const exists = await fs.pathExists(reportPath);
	if (!exists) {
		return [];
	}
	const contents = await fs.readFile(reportPath, 'utf8');
	const doc = new DOMParser().parseFromString(contents, 'text/xml');
	const suite_root = doc.documentElement.firstChild.tagName === 'testsuites' ? doc.documentElement.firstChild : doc.documentElement;
	const suites = Array.from(suite_root.getElementsByTagName('testsuite'));

	// We need to get the 'testcase' elements that have an 'error' or 'failure' child node
	const failed_suites = suites.filter(suite => {
		const hasFailures = suite.hasAttribute('failures') && parseInt(suite.getAttribute('failures')) !== 0;
		const hasErrors = suite.hasAttribute('errors') && parseInt(suite.getAttribute('errors')) !== 0;
		return hasFailures || hasErrors;
	});
	// Gather all the testcase nodes from each failed suite properly.
	let failed_suites_all_tests = [];
	failed_suites.forEach(function (suite) {
		failed_suites_all_tests = failed_suites_all_tests.concat(Array.from(suite.getElementsByTagName('testcase')));
	});
	return failed_suites_all_tests.filter(function (test) {
		return test.hasChildNodes() && (test.getElementsByTagName('failure').length > 0 || test.getElementsByTagName('error').length > 0);
	});
}

async function handleTestResults() {
	// Give details on failed mocha suite tests
	const failed = await Promise.all([
		gatherFailedTestcases(path.join(__dirname, 'junit.android.xml')),
		gatherFailedTestcases(path.join(__dirname, 'junit.ios.xml'))
	]);
	const failures_and_errors = [ ...failed[0], ...failed[1] ];
	if (failures_and_errors.length === 0) {
		return;
	}

	fail('Tests have failed, see below for more information.');
	let message = '### Tests: \n\n';
	const keys = Array.from(failures_and_errors[0].attributes).map(attr => attr.nodeName);
	const attributes = keys.map(key => {
		return key.substr(0, 1).toUpperCase() + key.substr(1).toLowerCase();
	});
	attributes.push('Error');

	// TODO Include stderr/stdout too?
	// Create the headers
	message += '| ' + attributes.join(' | ') + ' |\n';
	message += '| ' + attributes.map(() => '---').join(' | ') + ' |\n';

	// Map out the keys to the tests
	failures_and_errors.forEach(test => {
		const row_values = keys.map(key => test.getAttribute(key));
		// push error/failure message too
		const errors = test.getElementsByTagName('error');
		if (errors.length !== 0) {
			row_values.push(errors.item(0).getAttribute('message') + errors.item(0).getAttribute('stack'));
		} else {
			const failures = test.getElementsByTagName('failure');
			if (failures.length !== 0) {
				row_values.push(failures.item(0).getAttribute('message') + failures.item(0).getAttribute('stack'));
			} else {
				row_values.push(''); // This shouldn't ever happen
			}
		}
		message += '| ' + row_values.join(' | ') + ' |\n';
	});

	markdown(message);
}

// Add link to built SDK zipfile!
async function linkToSDK() {
	if (ENV.BUILD_STATUS === 'SUCCESS' || ENV.BUILD_STATUS === 'UNSTABLE') {
		const sdkLink = danger.utils.href(`${ENV.BUILD_URL}artifact/${ENV.ZIPFILE}`, 'Here\'s the generated SDK zipfile');
		message(`:floppy_disk: ${sdkLink}.`);
	}
}

async function main() {
	// do a bunch of things in parallel
	// Specifically, anything that collects what labels to add or remove has to be done first before...
	await Promise.all([
		checkNPMTestOutput(),
		checkStats(github.pr),
		checkJIRA(),
		checkPackageJSONInSync(),
		linkToSDK(),
		checkForIOSCrash(),
		handleTestResults(),
		checkChangedFileLocations(),
		checkCommunity(),
		checkMergeable(),
		checkPRisApproved(),
		updateMilestone()
	]);
	// ...once we've gathered what labels to add/remove, do that last
	await requestReviews();
	await removeLabels();
	await addMissingLabels();
}
main()
	.then(() => process.exit(0))
	.catch(err => {
		console.error(err);
		process.exit(1);
	});
// TODO Pass along any warnings/errors from eslint in a readable way? Right now we don't have any way to get at the output of the eslint step of npm test
// May need to edit Jenkinsfile to do a try/catch to spit out the npm test output to some file this dangerfile can consume?
// Or port https://github.com/leonhartX/danger-eslint/blob/master/lib/eslint/plugin.rb to JS - have it run on any edited/added JS files?
