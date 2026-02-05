#!/usr/bin/env node
/**
 * Prepare script that builds only if needed.
 * This allows npm install from GitHub to work without requiring all dev dependencies.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const pluginBuildPath = path.join(__dirname, '..', 'plugin', 'build', 'withVosk.js');
const libPath = path.join(__dirname, '..', 'lib', 'commonjs', 'index.js');

// Check if we're in a CI environment or npm install context
const isCI = process.env.CI === 'true';
const isNpmInstall = process.env.npm_command === 'install';

console.log('[prepare] Checking if build is needed...');

// Check if plugin is already built
const pluginExists = fs.existsSync(pluginBuildPath);
const libExists = fs.existsSync(libPath);

if (pluginExists && libExists) {
  console.log('[prepare] All build artifacts exist, skipping build.');
  process.exit(0);
}

// Try to build, but don't fail if dev dependencies are missing
try {
  // Check if bob is available
  try {
    require.resolve('react-native-builder-bob');
  } catch (e) {
    console.log('[prepare] Dev dependencies not installed, skipping build.');
    console.log('[prepare] This is expected when installing from GitHub.');
    console.log('[prepare] Plugin and lib should be pre-built in the repository.');
    
    if (!pluginExists) {
      console.error('[prepare] WARNING: plugin/build/withVosk.js is missing!');
      console.error('[prepare] Expo config plugin will not work.');
    }
    
    process.exit(0);
  }

  console.log('[prepare] Building library...');
  
  if (!libExists) {
    console.log('[prepare] Building lib with bob...');
    execSync('npx bob build', { stdio: 'inherit', cwd: path.join(__dirname, '..') });
  }
  
  if (!pluginExists) {
    console.log('[prepare] Building plugin...');
    execSync('npx tsc --project plugin/tsconfig.json', { stdio: 'inherit', cwd: path.join(__dirname, '..') });
  }
  
  console.log('[prepare] Build complete.');
} catch (error) {
  console.log('[prepare] Build failed, but continuing...');
  console.log('[prepare] Error:', error.message);
  
  // Don't fail the install - plugin/lib should be pre-built
  if (pluginExists) {
    console.log('[prepare] Pre-built plugin exists, install can continue.');
    process.exit(0);
  }
  
  process.exit(0); // Don't fail npm install
}
