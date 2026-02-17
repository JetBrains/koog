// Node script to invoke KoogAgent outside of a browser

import WebSocket from 'ws';
// Ensure WebSocket is available for the koogelis library (Kotlin/JS expects global WebSocket)
if (!globalThis.WebSocket) {
    globalThis.WebSocket = WebSocket;
}

// Import KoogAgent and AgentConfiguration from the built koogelis library
import {KoogAgent, AgentConfiguration} from 'koogelis';


function getEnv(name, fallback = undefined) {
    const v = process.env[name];
    if (v == null || v === '') return fallback;
    return v;
}

const apiKey = getEnv('API_KEY') || 'YOUR_API_TOKEN';
const modelId = 'local'; // can be also gemini-2.0-flash or gemini-2.5-flash-lite or gemini-2.5-pro
const temperature = parseFloat(getEnv('TEMPERATURE', '0.0'));


const myLogger = {
    info(message) {
        console.info('MY_INFO ' + message);
    },
    debug(message) {
        console.debug('MY_DEBUG ' + message);
    },
    trace(message) {
        console.trace('MY_TRACE ' + message);
    },
    warn(message) {
        console.warn('MY_WARN ' + message);
    },
    error(message) {
        console.error('MY_ERROR ' + message);
    },
};

const myPersistence = {
    getCheckpoints(agentId) {
        return [];
    },
    saveCheckpoint(agentId, agentCheckpointData) {
        console.info('Saving a checkpoint to the console: ' + agentCheckpointData);
    },
    getLatestCheckpoint(agentId) {
        return null;
    },
};

const controller = new AbortController()
const signal = controller.signal

const timeoutId = setTimeout(() => controller.abort(), 1500)

const mcpServer = new AgentConfiguration.ToolDefinition(
    AgentConfiguration.ToolType.MCP,
    'server-everything',
    new AgentConfiguration.ToolOptions(
        'http://localhost:3001/mcp',
        AgentConfiguration.TransportType.STREAMABLE_HTTP,
        ["Authorization"],
        ["Bearer token"]
    )
)

// Build the same configuration used in the React component
const agentConfig = new AgentConfiguration(
    'Expert',
    new AgentConfiguration.Llm(
        modelId,
        '',
        apiKey,
        new AgentConfiguration.LlmParams(temperature, 1000),
    ),
    AgentConfiguration.AgentStrategy.SINGLE_RUN,
    'You are a expert in Lithuanian national food. You can use ONLY get-sum tool, it is useful for calculating calories. Tinginys has 500 kcal, Cepelinai has 300 kcal, Kugelis has 250 kcal.',
    [], // you can pass mcpServer from above
    100,
    true,
    null, // possible to use myLogger
    null // possible to use myPersistence
);

async function main() {
    try {
        const koogAgent = KoogAgent.KoogFactory.create(agentConfig);
        const prompt = process.argv.slice(2).join(' ').trim() || 'Gimme a good joke about Kotlin/JS please';

        const result = await koogAgent.invoke(prompt); // or use the code below to abort after timeoutId
        // const result = await koogAgent.invoke(prompt, signal)
        //     .then(() => {
        //         clearTimeout(timeoutId)
        //         console.log("Finished")
        //     })
        //     .catch((err) => console.error(err.name === 'AbortError' ? 'Aborted' : 'Errored'));

        // Some Kotlin/JS wrappers may return objects; ensure string output
        const text = typeof result === 'string' ? result : String(result);
        console.log('\n=== Agent response ===');
        console.log(text);
    } catch (err) {
        console.error('Invocation failed:', err);
        process.exitCode = 1;
    }
}

// Top-level await supported in Node ESM
await main();
