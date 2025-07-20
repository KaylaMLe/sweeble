<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sweeble Changelog

## [Unreleased]

### Added

- OpenAI API key configuration in plugin settings with fallback to system environment
- Settings UI accessible via Settings > Tools > Sweeble AI Assistant
- Visual feedback when API key is not configured (orange suggestion text)
- Comprehensive error handling for missing API keys
- Unit tests for settings functionality

### Changed

- Improved API key resolution logic (settings → environment → error)
- Enhanced error messages with helpful configuration guidance
- Updated README with comprehensive documentation

### Planned

- **Next Edit Suggestion**: AI-powered code change suggestions (not just additions) - analyze existing code and suggest improvements, refactoring, and modifications

## [0.0.1] - 2024-07-19

### Added

- AI-powered inline code completions using OpenAI GPT-4o model
- Context-aware completions with 500 characters before and after cursor
- Automatic language detection based on file extensions
- Real-time ghost text suggestions as you type
- Comprehensive prompt engineering with detailed examples
- Inline completion provider integration with IntelliJ Platform
- OpenAI API integration with proper request/response handling
- JSON response parsing with error handling
- Logging and debugging support
- Test coverage for inline completion API behavior

### Changed

- Upgraded from GPT-3.5-turbo to GPT-4o model for better completions
- Increased max tokens from 20 to 100 for longer completions
- Enhanced prompt engineering with detailed examples and formatting instructions
- Improved language detection and registration
- Refactored code structure for better maintainability
- Optimized context extraction for better AI suggestions

### Fixed

- Language registration issues for various file types
- Kotlin suggestion prioritization
- Markdown file support
- Python plugin dependency handling
- Inline completion API integration issues

## [0.0.0] - 2024-07-16

### Added

- Initial project setup from IntelliJ Platform Plugin Template
- Basic Gradle configuration
- Plugin structure and dependencies
- Initial test framework setup
- Template cleanup and project configuration

---

**Note**: This changelog follows the [Keep a Changelog](https://keepachangelog.com/) format.
