# Sweeble

[![Build](https://github.com/KaylaMLe/sweeble/workflows/Build/badge.svg)](https://github.com/KaylaMLe/sweeble/actions)

<!-- Plugin description -->

Sweeble is an AI-powered inline completion plugin for IntelliJ IDEA that provides intelligent code suggestions as you type. It uses OpenAI's GPT-4o model to generate contextual code completions that appear as ghost text, similar to GitHub Copilot.

**Features:**

- 🤖 **AI-Powered Completions**: Uses OpenAI's GPT-4o model for intelligent code suggestions
- 🎯 **Context-Aware**: Analyzes 500 characters before and after cursor for better completions
- 🌍 **Multi-Language Support**: Works with Java, Kotlin, Python, JavaScript, TypeScript, C++, C#, PHP, Ruby, Go, Rust, Swift, Scala, SQL, HTML, CSS, XML, JSON, YAML, Markdown, and more
- ⚙️ **Flexible Configuration**: API key can be set in plugin settings or system environment
- 🚀 **Real-time Suggestions**: Provides instant inline completions as you type
- 🎨 **Visual Feedback**: Clear indication when API key needs to be configured

**Requirements:**

- IntelliJ IDEA 2024.2 or later (build 242+)
- OpenAI API key

<!-- Plugin description end -->

## 🚀 Installation

### For End Users

1. Open IntelliJ IDEA
2. Go to <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd>
3. Search for "Sweeble"
4. Click <kbd>Install</kbd>

### For Developers

#### Prerequisites

- Java 21 or later
- Gradle 8.13 or later
- IntelliJ IDEA 2024.2+ for development

#### Setup Development Environment

```bash
# Clone the repository
git clone https://github.com/KaylaMLe/sweeble.git
cd sweeble

# Build the plugin
./gradlew buildPlugin

# Run in development mode
./gradlew runIde
```

## ⚙️ Configuration

### Setting Up OpenAI API Key

#### Option 1: Plugin Settings (Recommended)

1. Go to <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Sweeble AI Assistant</kbd>
2. Enter your OpenAI API key
3. Click <kbd>Apply</kbd> and <kbd>OK</kbd>

#### Option 2: System Environment Variable

Set the `OPENAI_API_KEY` environment variable:

```bash
# Linux/macOS
export OPENAI_API_KEY="your-api-key-here"

# Windows (PowerShell)
$env:OPENAI_API_KEY="your-api-key-here"

# Windows (Command Prompt)
set OPENAI_API_KEY=your-api-key-here
```

### Getting an OpenAI API Key

1. Visit [OpenAI Platform](https://platform.openai.com/api-keys)
2. Sign in or create an account
3. Click "Create new secret key"
4. Copy the generated key and use it in the plugin settings

## 🛠️ Development

### Project Structure

```
sweeble/
├── src/main/kotlin/com/github/kaylamle/sweeble/
│   ├── inline/
│   │   └── SweebleInlineCompletionProvider.kt    # Main completion logic
│   └── services/
│       ├── OpenAIService.kt                      # OpenAI API integration
│       ├── SweebleSettingsState.kt               # Settings persistence
│       └── SweebleSettingsConfigurable.kt        # Settings UI
├── src/main/resources/META-INF/
│   └── plugin.xml                                # Plugin configuration
└── src/test/                                     # Unit tests
```

### Building

```bash
# Build the plugin
./gradlew buildPlugin

# Build with tests
./gradlew build

# Run tests only
./gradlew test
```

### Running in Development

```bash
# Run with a fresh IDE instance
./gradlew runIde

# Run with UI tests support
./gradlew runIdeForUiTests
```

### Key Components

#### SweebleInlineCompletionProvider

- Implements the IntelliJ Platform's inline completion API
- Extracts context (500 chars before/after cursor)
- Detects programming language automatically
- Handles API key validation and error states

#### OpenAIService

- Manages OpenAI API communication
- Implements API key resolution (settings → environment)
- Handles request/response parsing
- Provides error handling and logging

#### Settings Management

- `SweebleSettingsState`: Persistent storage for API key
- `SweebleSettingsConfigurable`: Settings UI in IDE preferences
- Automatic fallback to environment variables

### Testing

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests SweebleSettingsStateTest

# Run with coverage
./gradlew koverReport
```

## 🔧 Troubleshooting

### No Completions Appearing

1. **Check API Key**: Ensure your OpenAI API key is configured in Settings > Tools > Sweeble AI Assistant
2. **Check File Type**: Make sure you're editing a supported file type
3. **Check Logs**: Look for errors in the IDE's Event Log or Help > Show Log

### Orange Configuration Message

If you see an orange suggestion saying "Configure OpenAI API key...", it means no API key is found. Follow the configuration steps above.

### API Errors

- **401 Unauthorized**: Check your API key is correct
- **429 Rate Limited**: You've exceeded your OpenAI API rate limits
- **Network Issues**: Check your internet connection
