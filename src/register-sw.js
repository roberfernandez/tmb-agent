// Register before session checks can navigate to auth/.
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/tmb-agent/sw.js').catch(() => {});
}
