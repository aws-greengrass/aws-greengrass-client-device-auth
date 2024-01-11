/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

readJson = (file) => {
    const fs = require('fs');
    return JSON.parse(fs.readFileSync(file, 'utf8'));
};

jmhResultsAsMarkdownTable = (results) => {
    const header = '| Benchmark | Score |\n| --- | --- |';
    const rows = results.map(benchmarkInfo => {
        const benchmarkName = benchmarkInfo.benchmark;
        const score = benchmarkInfo.primaryMetric.score.toFixed(2);
        const scoreUnit = benchmarkInfo.primaryMetric.scoreUnit;
        return `| ${benchmarkName} | ${score} ${scoreUnit} |`;
    });
    return `${header}\n${rows.join('\n')}`;
};

replaceComment = async (github, context, prefix, body) => {
    console.log(`calling listComments(${context.issue.number}, ${context.repo.owner}, ${context.repo.repo})`)
    let listResp = await github.rest.issues.listComments({
        issue_number: context.issue.number,
        owner: context.repo.owner,
        repo: context.repo.repo,
    });
    console.log(`listComments resp: ${JSON.stringify(listResp)}`);

    const comment = listResp.data.find(comment => comment.body?.startsWith(prefix));
    if (comment) {
        console.log(`calling updateComment(${context.repo.owner}, ${context.repo.repo}, ${comment.id}, ${body})`);
        let updateResp = await github.rest.issues.updateComment({
            owner: context.repo.owner,
            repo: context.repo.repo,
            comment_id: comment.id,
            body: body,
        });
        console.log(`updateComment resp: ${JSON.stringify(updateResp)}`);
        return;
    } else {
        console.log(`no existing comment found with prefix: ${prefix}, creating new one...`)
    }

    console.log(`calling createComment(${context.issue.number}, ${context.repo.owner}, ${context.repo.repo}, ${body})}`);
    let createResp = await github.rest.issues.createComment({
        issue_number: context.issue.number,
        owner: context.repo.owner,
        repo: context.repo.repo,
        body: body,
    });
    console.log(`createComment resp: ${JSON.stringify(createResp)}`);
};

module.exports = async ({github, context, core}) => {
    const commentTitle = 'Benchmark Results';
    const commentHeader = `${commentTitle}\n---\n\n`;

    const {RESULTS_FILE} = process.env;
    const results = readJson(RESULTS_FILE);

    const resultsTable = jmhResultsAsMarkdownTable(results);
    const body = `${commentHeader}${resultsTable}`;
    await replaceComment(github, context, commentTitle, body);
};
