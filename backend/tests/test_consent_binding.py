import pytest

from app.consent_binding import (
    action_digest,
    consent_resource,
    workflow_resource,
)


def test_digest_is_stable_across_dictionary_order():
    assert action_digest("image_basic", {"width": 512, "steps": 20}) == action_digest(
        "image_basic", {"steps": 20, "width": 512}
    )


def test_digest_changes_when_any_action_parameter_changes():
    baseline = consent_resource("image_basic", {"width": 512, "steps": 20})
    assert baseline != consent_resource("image_basic", {"width": 768, "steps": 20})
    assert baseline != consent_resource("image_basic", {"width": 512, "steps": 21})
    assert baseline != consent_resource("video_basic", {"width": 512, "steps": 20})


def test_rejects_non_finite_json_values():
    with pytest.raises(ValueError):
        action_digest("image_basic", {"cfg": float("nan")})


def test_resource_contains_workflow_and_digest():
    resource = consent_resource("image_basic", {"width": 512})
    assert resource.startswith("workflow:image_basic:sha256:")
    assert len(resource.rsplit(":", 1)[-1]) == 64


def test_workflow_resource_is_compatible_alias():
    parameters = {"width": 512, "steps": 20}
    assert workflow_resource("image_basic", parameters) == consent_resource(
        "image_basic", parameters
    )
