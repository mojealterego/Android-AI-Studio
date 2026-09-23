from app.main import comfy_websocket_url

def test_comfy_websocket_url_converts_http():
    assert comfy_websocket_url("client/1") == "ws://127.0.0.1:8188/ws?clientId=client%2F1"

def test_comfy_websocket_url_converts_https():
    import app.main as main
    old = main.COMFYUI
    main.COMFYUI = "https://gpu.internal:8188"
    try:
        assert main.comfy_websocket_url("abc") == "wss://gpu.internal:8188/ws?clientId=abc"
    finally:
        main.COMFYUI = old
