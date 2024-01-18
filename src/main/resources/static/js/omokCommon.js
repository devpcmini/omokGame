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
            case 'login' :
                document.querySelector('#myId').innerText = '아이디 : ' + receivedMessage.data;
                break;
            case 'error' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'start' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'end' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'message' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'sendMessage' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'roomList' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'createRoom' :
                break;
            case 'joinRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = 'none';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = '';
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'leaveRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = '';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = 'none';
                break;
            case 'changeRole'  :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'move' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'invite' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            default :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
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

//알럿 닫기
function onAlertClick(){
    document.querySelector('.alertPopup').style.display = 'none';
    window.parent.document.querySelector('#parent_overlay').style.display = 'none';
}