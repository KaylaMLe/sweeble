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
- 🔧 **Smart Code Analysis**: Automatically detects when simple insertions can complete logical units vs when complex edits are needed
- 🎯 **Intelligent Offset Calculation**: Programmatically calculates text offsets for precise replacements
- 🔍 **Fuzzy Text Matching**: Handles whitespace variations and partial matches for robust text replacement
- 🎨 **Customizable Appearance**: Configurable inline suggestion colors and highlighting

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

## 🎯 Usage

### Inline Completions

Sweeble provides intelligent inline completions as you type. Simply start typing in any supported file type, and you'll see ghost text suggestions appear with a customizable background color.

### Smart Code Analysis

Sweeble intelligently analyzes your code to determine the best type of suggestion:

1. **Simple Insertions**: When a logical unit can be completed with just additional text
2. **Complex Edits**: When existing code needs to be modified or corrected
3. **No Suggestions**: When the code is complete and no suggestions are needed

The plugin automatically detects the context and provides appropriate suggestions without manual intervention.

### Visual Feedback

- **Inline Suggestions**: Appear as ghost text with customizable background color
- **Error States**: Clear indication when API key needs to be configured
- **Smart Highlighting**: Entire lines are highlighted for complex edits to show what will be changed

**Examples:**

- **Simple Insertion**: Completing a function call with missing parameters
- **Complex Edit**: Fixing a mistyped function parameter or adding missing braces
- **Multiple Changes**: Suggesting fixes for syntax errors across multiple lines

The plugin prioritizes the smallest possible changes and limits scope to the current logical unit (function, class, etc.).

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
│   │   └── SweebleMainPlugin.kt                 # Main completion logic
│   ├── services/
│   │   ├── OpenAIService.kt                     # OpenAI API integration
│   │   ├── OffsetCalculationService.kt          # Text offset calculation
│   │   ├── ChangeHighlighter.kt                 # Visual highlighting
│   │   ├── SweebleSettingsState.kt              # Settings persistence
│   │   └── SweebleSettingsConfigurable.kt       # Settings UI
│   └── startup/
│       └── MyProjectActivity.kt                 # Plugin startup
├── src/main/resources/META-INF/
│   └── plugin.xml                               # Plugin configuration
└── src/test/                                    # Unit tests
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

#### SweebleMainPlugin

- Implements the IntelliJ Platform's inline completion API
- Extracts context (500 chars before/after cursor)
- Detects programming language automatically
- Handles API key validation and error states
- Manages suggestion classification and rendering

#### OpenAIService

- Manages OpenAI API communication
- Implements API key resolution (settings → environment)
- Handles request/response parsing
- Provides error handling and logging
- Generates both simple completions and complex edit suggestions

#### OffsetCalculationService

- Programmatically calculates text offsets for precise replacements
- Handles cursor markers and whitespace variations
- Implements fuzzy text matching for robust replacements
- Strips cursor indicators from AI responses before matching

#### ChangeHighlighter

- Renders visual feedback for code changes
- Highlights entire lines for complex edits
- Manages inlay hints for multiline suggestions
- Handles cleanup of visual elements

#### Settings Management

- `SweebleSettingsState`: Persistent storage for API key
- `SweebleSettingsConfigurable`: Settings UI in IDE preferences
- Automatic fallback to environment variables

### Testing

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests OffsetCalculationServiceTest

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

### Visual Issues

- **Inlay Overflow**: Multiline suggestions should now properly fit within the editor
- **Color Visibility**: Inline suggestion colors can be customized in the code
- **Highlighting**: Entire lines are highlighted for complex edits to show scope
