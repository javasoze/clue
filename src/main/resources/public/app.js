const commandList = document.getElementById("command-list");
const commandFilter = document.getElementById("command-filter");
const commandInput = document.getElementById("command-input");
const argsInput = document.getElementById("args-input");
const runForm = document.getElementById("run-form");
const runButton = document.getElementById("run-button");
const output = document.getElementById("output");
const statusPill = document.getElementById("connection-status");
const commandCount = document.getElementById("command-count");
const lastRun = document.getElementById("last-run");
const historyList = document.getElementById("history-list");
const copyButton = document.getElementById("copy-output");

const state = {
  commands: [],
  history: [],
};

function setStatus(stateName, text) {
  statusPill.dataset.state = stateName;
  statusPill.textContent = text;
}

function renderCommands(commands) {
  commandList.innerHTML = "";
  if (!commands.length) {
    const empty = document.createElement("div");
    empty.className = "panel-subtitle";
    empty.textContent = "No commands match your filter.";
    commandList.appendChild(empty);
    return;
  }
  commands.forEach((cmd, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "command-button";
    button.innerHTML = `<div>${cmd}</div><span>#${String(index + 1).padStart(2, "0")}</span>`;
    button.addEventListener("click", () => {
      commandInput.value = cmd;
      commandInput.focus();
    });
    commandList.appendChild(button);
  });
}

function renderHistory() {
  historyList.innerHTML = "";
  if (!state.history.length) {
    const empty = document.createElement("div");
    empty.className = "panel-subtitle";
    empty.textContent = "Run a command to build history.";
    historyList.appendChild(empty);
    return;
  }
  state.history.forEach((entry) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "history-chip";
    chip.textContent = entry.label;
    chip.addEventListener("click", () => {
      commandInput.value = entry.command;
      argsInput.value = entry.args;
      commandInput.focus();
    });
    historyList.appendChild(chip);
  });
}

function updateLastRun() {
  const now = new Date();
  lastRun.textContent = now.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function addHistory(command, args) {
  const label = args ? `${command} ${args}` : command;
  state.history.unshift({ command, args, label });
  state.history = state.history.slice(0, 6);
  renderHistory();
}

async function loadCommands() {
  setStatus("loading", "Loading commands...");
  try {
    const res = await fetch("/clue/commands");
    if (!res.ok) {
      throw new Error(`Server returned ${res.status}`);
    }
    const data = await res.json();
    state.commands = data.sort();
    commandCount.textContent = state.commands.length;
    renderCommands(state.commands);
    renderHistory();
    setStatus("ok", "Ready to run");
  } catch (err) {
    setStatus("error", "API unavailable");
    output.textContent = `Unable to reach /clue/commands.\n${err.message}`;
  }
}

function filterCommands() {
  const filter = commandFilter.value.trim().toLowerCase();
  const filtered = state.commands.filter((cmd) =>
    cmd.toLowerCase().includes(filter)
  );
  renderCommands(filtered);
}

async function runCommand() {
  const command = commandInput.value.trim();
  const args = argsInput.value.trim();
  if (!command) {
    output.textContent = "Pick a command from the list or type one.";
    return;
  }
  runButton.disabled = true;
  output.textContent = "Running...";
  try {
    const url = `/clue/command/${encodeURIComponent(command)}?args=${encodeURIComponent(args)}`;
    const res = await fetch(url);
    const text = await res.text();
    output.textContent = text || "(no output)";
    updateLastRun();
    addHistory(command, args);
    if (res.ok) {
      setStatus("ok", "Command complete");
    } else if (res.status === 404) {
      setStatus("error", "Command not found");
    } else {
      setStatus("error", "Command failed");
    }
  } catch (err) {
    output.textContent = `Failed to run command.\n${err.message}`;
    setStatus("error", "Network error");
  } finally {
    runButton.disabled = false;
  }
}

copyButton.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(output.textContent);
    copyButton.textContent = "Copied!";
    setTimeout(() => {
      copyButton.textContent = "Copy output";
    }, 1200);
  } catch (err) {
    output.textContent += "\nCopy failed.";
  }
});

commandFilter.addEventListener("input", filterCommands);
runForm.addEventListener("submit", (event) => {
  event.preventDefault();
  runCommand();
});

loadCommands();
