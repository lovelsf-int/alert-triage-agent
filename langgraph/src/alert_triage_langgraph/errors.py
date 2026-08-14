class AlertTriageError(Exception):
    """Base exception for application failures."""


class ConfigurationError(AlertTriageError):
    """Raised when required runtime configuration is missing or invalid."""


class ExternalServiceError(AlertTriageError):
    """Raised when a model or embedding provider returns an unusable response."""


class AnalysisNotFoundError(AlertTriageError):
    """Raised when a LangGraph thread cannot be found."""


class InvalidReviewStateError(AlertTriageError):
    """Raised when a review is submitted for a non-interrupted analysis."""
