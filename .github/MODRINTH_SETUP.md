# Modrinth Auto-Publishing Setup

This repository is configured to automatically publish releases to Modrinth when a new GitHub release is created.

## Required Setup

### 1. Create a Modrinth API Token

1. Go to your Modrinth account settings: https://modrinth.com/settings/pats
2. Click on "Create a token"
3. Give it a descriptive name (e.g., "GitHub Actions - Backuper")
4. Select the following scopes:
   - `CREATE_VERSION` - Required to publish new versions
   - `PROJECT:WRITE` - Required to manage project versions
5. Copy the generated token (you won't be able to see it again!)

### 2. Add the Token to GitHub Secrets

1. Go to your GitHub repository settings: https://github.com/DVDishka/Backuper/settings/secrets/actions
2. Click on "New repository secret"
3. Name: `MODRINTH_TOKEN`
4. Value: Paste the token you copied from Modrinth
5. Click "Add secret"

## How It Works

The workflow is triggered automatically when you:
1. Create a new release on GitHub
2. The workflow will:
   - Build the plugin JAR using Maven
   - Upload it to Modrinth with the release version and changelog
   - Update the Modrinth project description with README.md content
   - Set the version type to "release"
   - Tag it with Paper and Folia loaders

## Testing

To test the workflow:
1. Create a new GitHub release
2. The workflow will start automatically
3. Check the "Actions" tab to see the progress
4. Once completed, the new version should appear on Modrinth: https://modrinth.com/plugin/backuper

## Customization

You can customize the workflow by editing `.github/workflows/modrinth-publish.yml`:
- Modify supported game versions
- Change loaders
- Adjust version type (release, beta, alpha)
- Add additional publishing targets (CurseForge, Hangar, etc.)

## Troubleshooting

If the workflow fails:
1. Check that the `MODRINTH_TOKEN` secret is set correctly
2. Verify the token has the required permissions
3. Check the Actions logs for detailed error messages
4. Ensure the Modrinth project ID matches: `Backuper`
