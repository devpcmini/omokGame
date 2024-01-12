window.onload = function() {
    let childFrame;
    const socket = new WebSocket('ws://' + ip + ':8080/ws');
    socket.onopen = function (event) {
        sendMessageToServer({type: 'roomList'});
    };

    socket.onmessage = function (event) {
        // 서버로부터 수신된 메시지 처리 (게임 상태 업데이트 등)
        let receivedMessage;
        if (isJSON(event.data)) {
            receivedMessage = JSON.parse(event.data);
        }
        // 플레이어 간 통신 메시지 처리 로직...
        childFrame = document.getElementById('frameElement');
        switch (receivedMessage.type) {
            case 'error' :
                document.querySelector('.alertPopup_text').innerText = receivedMessage.data;
                document.querySelector('.alertPopup').style.display = '';
                break;
            case 'start' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'message' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'roomList' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'createRoom' :
                break;
            case 'joinRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = 'none';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = '';
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'leaveRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = '';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = 'none';
                break;
            case 'changeRole'  :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'move' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'end' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            default :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
        }
    };

    socket.onclose = function (event) {
        console.log('WebSocket connection closed:', event);
    };

    // 이벤트 발생 시 서버로 메시지 전송
    function sendMessageToServer(message) {
        socket.send(JSON.stringify(message));
    }

    //JSON 형식인지 확인하기
    function isJSON(str) {
        return /^[\],:{}\s]*$/.test(str.replace(/\\["\\\/bfnrtu]/g, '@').replace(/"[^"\\\n\r]*"|true|false|null|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?/g, ']').replace(/(?:^|:|,)(?:\s*\[)+/g, ''));
    }

    window.addEventListener("message", function (message) {
        if (message.data instanceof Object) {
            sendMessageToServer(message.data);
        }
    });
};

function onAlertClick(){
    document.querySelector('.alertPopup').style.display = 'none';
}