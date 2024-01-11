window.onload = function() {
    let childFrame;
    const socket = new WebSocket('ws://172.30.1.38:8080/ws');
    socket.onopen = function (event) {
        console.log('WebSocket connection opened:', event);
        sendMessageToServer({type: 'room_list'});
    };

    socket.onmessage = function (event) {
        // 서버로부터 수신된 메시지 처리 (게임 상태 업데이트 등)
        let receivedMessage;
        if (isJSON(event.data)) {
            receivedMessage = JSON.parse(event.data);
        } else {
            alert(event.data);
            return;
        }
        // 플레이어 간 통신 메시지 처리 로직...
        childFrame = document.getElementById('frameElement');
        switch (receivedMessage.type) {
            case 'message' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'room_list' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'room_new' :
                break;
            case 'room_enter' :
                childFrame.contentWindow.document.querySelector('.waiting-room').style.display = 'none';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = '';
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'room_leave' :
                childFrame.contentWindow.document.querySelector('.waiting-room').style.display = '';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = 'none';

                break;
            case 'player_change'  :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'player_selected' :
                childFrame.contentWindow.postMessage(receivedMessage, 'http://' + ip + ':8080');
                break;
            case 'game_end' :
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


    function isJSON(str) {
        if (/^[\],:{}\s]*$/.test(str.replace(/\\["\\\/bfnrtu]/g, '@').replace(/"[^"\\\n\r]*"|true|false|null|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?/g, ']').replace(/(?:^|:|,)(?:\s*\[)+/g, ''))) {
            return true;
        } else {
            return false;
        }
    }
    window.addEventListener("message", function (message) {
        if (message.data instanceof Object) {
            sendMessageToServer(message.data);
        }
    });
};

function onAlertClick(){
    document.querySelector('.alertscreen').style.display = 'none';
}