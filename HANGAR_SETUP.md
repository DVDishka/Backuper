# Hangar Publishing Setup Guide

This guide explains how to configure the repository for automatic Hangar publishing when creating new GitHub releases.

## Overview

The workflow in `.github/workflows/publish.yml` automatically publishes the plugin to both Modrinth and Hangar when a new GitHub release is created. The Hangar version references the Modrinth download link instead of uploading the file directly.

## Prerequisites

1. **Hangar Account**: You need an account on [Hangar](https://hangar.papermc.io/)
2. **Hangar Project**: The project "Backuper" must be created on Hangar at `https://hangar.papermc.io/Collagen/Backuper`
3. **Hangar API Token**: You need to generate an API token from your Hangar account settings

## Required Configuration

### 1. Generate Hangar API Token

1. Log in to [Hangar](https://hangar.papermc.io/)
2. Go to your account settings
3. Navigate to the API Keys section
4. Create a new API key with the following permissions:
   - `create_version` - Required to create new versions
   - `edit_version` - Required to edit version details
   - `edit_page` - Required to update the project page with README.md
5. Copy the generated token (you won't be able to see it again)

### 2. Add GitHub Secret

1. Go to your GitHub repository: `https://github.com/DVDishka/Backuper`
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add the following secret:
   - **Name**: `HANGAR_TOKEN`
   - **Value**: Paste the API token you generated from Hangar
5. Click **Add secret**

### 3. Verify Workflow Configuration

The workflow file `.github/workflows/publish.yml` is already configured with:

- **Workflow name**: "Publish to Modrinth and Hangar"
- **Trigger**: Runs automatically when a new GitHub release is published
- **Jobs**:
  1. `publish-modrinth`: Builds and publishes to Modrinth
  2. `publish-hangar`: Publishes to Hangar using external Modrinth link

## How It Works

### Workflow Execution

1. **Release Creation**: When you create a new GitHub release, the workflow is triggered
2. **Modrinth Publishing**: 
   - Builds the plugin JAR file using Maven
   - Uploads the JAR to Modrinth
   - Updates Modrinth project description with README.md
3. **Hangar Publishing**:
   - Waits for Modrinth upload to complete
   - Fetches the Modrinth download URL via API (with retry logic)
   - Creates a new version on Hangar pointing to the Modrinth download link
   - Updates Hangar project page with README.md content

### Key Features

- **External Download**: Hangar versions link to Modrinth instead of uploading the file twice
- **Automatic README Sync**: Both Modrinth and Hangar project pages are updated with README.md
- **Retry Logic**: Includes 5 retry attempts with 10-second intervals to handle API timing issues
- **Error Handling**: Fails gracefully with clear error messages if the Modrinth URL cannot be retrieved

## Testing

After setting up the `HANGAR_TOKEN` secret:

1. Create a new GitHub release (or draft release for testing)
2. Go to **Actions** tab in your repository
3. Watch the "Publish to Modrinth and Hangar" workflow execution
4. Verify that both jobs complete successfully:
   - `publish-modrinth` should show successful upload to Modrinth
   - `publish-hangar` should show successful version creation on Hangar
5. Check the [Hangar project page](https://hangar.papermc.io/Collagen/Backuper) to confirm:
   - New version appears in the versions list
   - Download link points to Modrinth
   - Project description is updated with README.md

## Troubleshooting

### Common Issues

**Problem**: "Error: Failed to retrieve Modrinth download URL"
- **Cause**: Modrinth API hasn't processed the upload yet
- **Solution**: The workflow includes retry logic, but if this persists, Modrinth might be experiencing issues

**Problem**: "Error: Authentication failed" during Hangar publish
- **Cause**: Invalid or missing `HANGAR_TOKEN` secret
- **Solution**: Verify the token is correct and has the required permissions

**Problem**: Workflow doesn't trigger
- **Cause**: Release is in draft mode or not published
- **Solution**: Make sure to publish the release (not just save as draft)

### Viewing Workflow Logs

To debug issues:
1. Go to **Actions** tab in your repository
2. Click on the failed workflow run
3. Expand the failed job and step to see detailed logs
4. The "Get Modrinth download URL" step shows retry attempts and API responses

## Project Configuration

The workflow uses the following project identifiers:
- **Modrinth Project ID**: `Backuper`
- **Hangar Project ID**: `Backuper`
- **Modrinth Project URL**: `https://modrinth.com/plugin/backuper`
- **Hangar Project URL**: `https://hangar.papermc.io/Collagen/Backuper`

## Supported Versions

The workflow is configured to publish for:
- **Loaders**: Paper, Folia
- **Game Versions**: 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.9, 1.21.10

To update supported versions, edit the `game-versions` section in both jobs in `.github/workflows/publish.yml`.

## Additional Notes

- The workflow uses `Kir-Antipov/mc-publish@v3.3` action for publishing
- Both secrets (`MODRINTH_TOKEN` and `HANGAR_TOKEN`) are required for the workflow to work completely
- If only `MODRINTH_TOKEN` is present, the Modrinth job will succeed but Hangar job will fail
- The workflow sets `fail-if-version-exists: false` to allow re-running failed publishes
