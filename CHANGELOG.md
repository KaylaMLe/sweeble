<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sweeble Changelog

## [Unreleased]

### Added

- **Intelligent Offset Calculation**: Programmatically calculates text offsets for precise replacements instead of relying on AI-provided offsets
- **Fuzzy Text Matching**: Handles whitespace variations and partial matches for robust text replacement
- **Cursor Marker Handling**: Strips `[CURSOR_HERE]` markers from AI responses before text matching
- **Multiline Inlay Support**: Proper height calculation for multiline suggestions to prevent overflow
- **Customizable Text Colors**: Configurable foreground and background colors for inline suggestions
- **Complete Line Replacement**: AI now provides complete corrected lines instead of partial replacements
- **Enhanced Visual Feedback**: Improved highlighting and inlay rendering for better user experience
- **Comprehensive Test Coverage**: Tests for offset calculation, cursor marker handling, and multiline rendering

### Changed

- **AI Prompt Engineering**: Updated system prompt to request complete corrected lines rather than partial text
- **Offset Calculation Logic**: Moved from AI-provided offsets to programmatic calculation for better accuracy
- **Visual Appearance**: Changed inline suggestion background from light blue to low opacity turquoise for better readability
- **Text Matching Strategy**: Implemented fuzzy matching with normalized whitespace handling
- **Inlay Rendering**: Fixed multiline inlay overflow by implementing proper height calculation
- **Highlighting Behavior**: Entire lines are now highlighted for complex edits to show complete scope

### Fixed

- **Multiline Inlay Overflow**: Inlays now properly fit within editor bounds with correct height calculation
- **Cursor Marker Interference**: Text matching no longer fails due to `[CURSOR_HERE]` markers in AI responses
- **Partial Line Replacement**: AI now provides complete corrected lines, preventing partial replacements
- **Coordinate System Mismatches**: Fixed offset calculation issues between AI context and editor document
- **Visual Readability**: Improved inline suggestion colors for better visibility across different themes
- **Duplicate Test Functions**: Consolidated and cleaned up test cases

### Technical Improvements

- **OffsetCalculationService**: New service for programmatic offset calculation with fuzzy matching
- **ChangeHighlighter**: Enhanced visual rendering with proper multiline support
- **OpenAIService**: Updated prompt engineering and response parsing for complete line corrections
- **Test Infrastructure**: Comprehensive test coverage for new offset calculation and visual rendering features

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
