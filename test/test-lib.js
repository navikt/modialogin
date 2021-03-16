const useColor = process.env.CI !== 'true';
const RED = useColor ? '\x1b[31m' : '';
const GREEN = useColor ? '\x1b[32m' : '';
const CYAN = useColor ? '\x1b[36m' : '';
const RESET = useColor ? '\x1b[0m' : '';

const tests = [];
const testsWithErrors = [];
let __assertions = [];
let __scheduled = null;

function test(name, exec) {
    tests.push({name, exec});
    clearTimeout(__scheduled);
    __scheduled = setTimeout(runTests, 0);
}

function runTests() {
    (async function asynsTestRunner() {
        console.log('');
        for (const { name, exec } of tests) {
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
        }
        if (testsWithErrors.length > 0) {
            console.log('');
            console.log(`${RED}Not all tests passed, found ${testsWithErrors.length} failing tests.${RESET}`);
            console.log('');
        }
    })();
}

function assertThat(actual, expected, message) {
    const verifier = typeof expected === 'function' ? expected : (() => {
        const result = JSON.stringify(actual) === JSON.stringify(expected);
        return { result, expected: JSON.stringify(expected) };
    });
    const verification = verifier(actual);
    if (verification.result) {
        __assertions.push({ state: 'ok', message});
    } else {
        __assertions.push({ state: 'error', message: `${message}. Expected: '${verification.expected}', but got: '${verification.actual}'`});
    }
}

const isDefined = (value) => ({
    result: value !== null && value !== undefined,
    expected: '<any value>',
    actual: value
});
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
const hasLengthGreaterThen = (minLength) => (value) => ({
    result: value && value.length > minLength,
    expected: 'value.length > ' + minLength,
    actual: `value.length == ${value && value.length || undefined} (${value})`
});

module.exports = {
    test, assertThat,
    isDefined, startsWith, contains, hasLengthGreaterThen
};