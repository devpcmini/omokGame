window.onload = function() {
    let childFrame = document.getElementById('frameElement');
    // Initialize Firebase
    firebase.initializeApp({
        apiKey: "AIzaSyCScK2DGrTe-lSdi7euF81ilaLygrEMcYo",
        authDomain: "omokgame-8c565.firebaseapp.com",
        projectId: "omokgame-8c565",
        storageBucket: "omokgame-8c565.appspot.com",
        messagingSenderId: "626400468296",
        appId: "1:626400468296:web:f146d30869018aa54de77d",
        measurementId: "G-M46FN291B5"
    });

    const messaging = firebase.messaging();
    const publicVapidKey = 'BDpyANOK-A_F167S6epNvW9p6Z-l_bUoZem0gyW8jgnHaRpTyqbk4UjOP7PzLdX6qdLHMoUynaj_4_3exq8PNuE';
    messaging.usePublicVapidKey(publicVapidKey);
    messaging.getToken().then(function(currentToken) {
        console.log('currentToken: ' + currentToken);
        if (currentToken) {
            childFrame.contentWindow.document.querySelector('#token').value = currentToken;
            console.log ('Token get - ' + currentToken);
        } else {
            console.log('No Instance ID token available. Request permission to generate one.');
        }
    }).catch(function(err) {
        console.log('Error retrieving Instance ID token. ', err);
    })

    const socket = new WebSocket('ws://' + ip + ':8080/ws');
    socket.onopen = function (event) {
        console.log("open socket")
        socket.send(JSON.stringify({type: 'connect',token : childFrame.contentWindow.document.querySelector('#token').value}));
    };

    socket.onmessage = function (event) {
        // 서버로부터 수신된 메시지 처리 (게임 상태 업데이트 등)
        let receivedMessage;
        if (isJSON(event.data)) {
            receivedMessage = JSON.parse(event.data);
        }
        // 플레이어 간 통신 메시지 처리 로직...
        switch (receivedMessage.type) {
            case 'login' :
                if(receivedMessage.data.indexOf('입력한 정보가 올바르지 않습니다.') == -1) {
                    document.querySelector('#myId').innerText = '아이디 : ' + receivedMessage.data;
                }
                childFrame.contentWindow.postMessage(receivedMessage, '*');
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