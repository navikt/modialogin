const useColor = process.env.CI !== 'true';
const RED = useColor ? '\x1b[31m' : '';
const GREEN = useColor ? '\x1b[32m' : '';
const CYAN = useColor ? '\x1b[36m' : '';
const RESET = useColor ? '\x1b[0m' : '';

const tests = [];
const testsWithErrors = [];
let __assertions = [];
let __scheduled = null;
let __preconditions = [];

function setup(name, exec) {
    __preconditions.push({ name, exec })
}

function test(name, exec) {
    tests.push({name, exec});
    clearTimeout(__scheduled);
    __scheduled = setTimeout(runTests, 0);
}

function runTests() {
    (async function asynsTestRunner() {
        console.log('');
        for (const { name, exec } of __preconditions) {
            console.log(`${CYAN}[ UP ]${RESET} ${name}`);
            await exec();
            console.log();
        }
        for (const {name, exec} of tests) {
            try {
                __assertions = [];
                await exec();
                const errors = __assertions.filter((assertion) => assertion.state !== 'ok');
                console.log(`${CYAN}[TEST]${RESET} ${name}`);
                __assertions
                    .forEach((assertion) => {
                        const prefix = assertion.state === 'ok' ? `${GREEN} [OK] ${RESET}` : `${RED} [KO] ${RESET}`;
                        console.log(`${prefix} ${assertion.message}`);
                    });
                console.log('');
                if (errors.length > 0) {
                    testsWithErrors.push(name);
                }
            } catch (e) {
                console.log(`${RED} [ERROR] ${RESET} "${name}" threw exception, exiting with non-zero exit code.`, e);
                testsWithErrors.push(name);
            }
        }
        if (testsWithErrors.length > 0) {
            console.log('');
            console.log(`${RED}Not all tests passed, found ${testsWithErrors.length} failing tests.${RESET}`);
            console.log('');
            process.exit(1);
        }
    })();
}

function assertThat(actual, expected, message) {
    const verifier = typeof expected === 'function' ? expected : (() => {
        const result = JSON.stringify(actual) === JSON.stringify(expected);
        return {result, expected: JSON.stringify(expected), actual };
    });
    const verification = verifier(actual);
    if (verification.result) {
        __assertions.push({state: 'ok', message});
    } else {
        __assertions.push({
            state: 'error',
            message: `${message}. Expected: '${verification.expected}', but got: '${verification.actual}'`
        });
    }
}

function verify(actual, expected, message) {
    const verifier = typeof expected === 'function' ? expected : (() => {
        const result = JSON.stringify(actual) === JSON.stringify(expected);
        return {result, expected: JSON.stringify(expected), actual };
    });

    const verification = verifier(actual);
    if (verification.result) {
        console.log(`${GREEN} [OK] ${RESET} ${message}`);
    } else {
        console.log(`${RED} [KO] ${RESET} ${message}. Expected: '${verification.expected}', but got: '${verification.actual}'`);
        throw new Error('retry');
    }
}

const sleep = (seconds) => new Promise((resolve) => setTimeout(resolve, seconds * 1000));
function retry({ retry, interval }, exec) {
    let count = 0;
    return async () => {
        do {
            try {
                await exec(count + 1);
                return;
            } catch (e) {
                console.log(`${RED} [KO] ${RESET} ${e}`);
            }
            count++;
            await sleep(interval);
        } while (count < retry);
        console.log();
        console.log(`${RED} [KO] ${RESET} Setup failed. Exiting...`);
        console.log();
        process.exit(1);
    };
}

const isDefined = (value) => ({
    result: value !== null && value !== undefined,
    expected: '<any value>',
    actual: value
});
const isNotDefined = value => ({
    result: value === null || value === undefined,
    expected: '<not defined>',
    actual: value
})
const startsWith = (prefix) => (value) => ({
    result: value && value.startsWith(prefix),
    expected: 'value.startsWith(' + prefix + ')',
    actual: value
});
const contains = (content) => (value) => ({
    result: value && value.includes(content),
    expected: 'value.includes(' + content + ')',
    actual: value
});
const notContains = (content) => (value) => ({
    result: value && !value.includes(content),
    expected: 'not(value.includes(' + content + '))',
    actual: value
});
const hasLengthGreaterThen = (minLength) => (value) => ({
    result: value && value.length > minLength,
    expected: 'value.length > ' + minLength,
    actual: `value.length == ${value && value.length || undefined} (${value})`
});

module.exports = {
    test, assertThat, setup, verify, retry,
    isDefined, isNotDefined, startsWith, contains, notContains, hasLengthGreaterThen
};