window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        switch (receivedMessage.type) {
            case 'room_list' :
                RoomList({ roomList: receivedMessage.data.map(room => ({ name: room.name }))});
                break;
            case 'create' :
                break;
            case 'join' :
                if(receivedMessage.roomName == 'empty' && receivedMessage.errorCode == 'fail'){
                    alert("방없음");
                    return;
                }
                break;
            case 'rooms' :
                const rooms = document.querySelector('#rooms');
                rooms.innerHTML = '';
                receivedMessage.roomInfos.forEach(roomInfo => {
                    if(roomInfo.playerCount != 0) {
                        // 방 목록에 추가될 요소 생성
                        const roomElement = document.createElement('div');
                        roomElement.classList.add('room');

                        // 방 이름 표시
                        const roomNameElement = document.createElement('span');
                        roomNameElement.style.marginRight = 'auto';
                        roomNameElement.textContent = roomInfo.roomName;

                        const spanTooltip = document.createElement('div');
                        spanTooltip.className = 'tooltip';
                        spanTooltip.id = 'tooltip';
                        spanTooltip.textContent = roomInfo.nicknames.join('\n');

                        // 참여 인원 표시
                        const participantsElement = document.createElement('span');
                        participantsElement.className = 'tooltip-trigger';
                        participantsElement.style.marginLeft = 'auto';
                        participantsElement.textContent = '참여 인원: ' + roomInfo.playerCount;
                        participantsElement.addEventListener("mouseout", hideTooltip);
                        participantsElement.addEventListener("mouseover", showTooltip);

                        // 입장 버튼 생성
                        const enterButton = document.createElement('button');
                        enterButton.textContent = '입장';
                        enterButton.classList.add('enter-button');
                        enterButton.style.display = roomInfo.playerCount == 2 ? 'none' : '';
                        enterButton.addEventListener('click', () => enterRoom(roomInfo.roomName));

                        // 방 이름과 입장 버튼을 방 목록에 추가
                        roomElement.appendChild(roomNameElement);
                        roomElement.appendChild(participantsElement);
                        participantsElement.appendChild(spanTooltip);
                        roomElement.appendChild(enterButton);

                        // 방 목록 컨테이너에 방 추가
                        rooms.appendChild(roomElement);
                    }
                });
                break;
        }
    }
});

function ParentToMessage(message) {
    window.parent.postMessage(message);
}

function enterRoom(roomName) {
// TODO: 방에 입장하는 로직 구현
    console.log(`Entering room: ${roomName}`);
}

// 방 생성 함수
function createRoom() {
    const roomName = document.getElementById('roomNameInput').value;
    const createRoomMessage = {type: 'create', roomName: roomName,
        nickName : top.document.querySelector('#loginNickname').textContent.replace('NickName : ','') ,stoneColor : "black"};
    ParentToMessage(createRoomMessage);
}

// 방 참가 함수
function joinRoom() {
    const roomName = document.getElementById('roomNameInput').value;
    const joinRoomMessage = {type: 'join', roomName: roomName,
        nickName : top.document.querySelector('#loginNickname').textContent.replace('NickName : ','')};
    ParentToMessage(joinRoomMessage);
}
//
// const roomListElement = document.getElementById('room-list-container');
//
// function renderRoomList(list) {
//     roomListElement.innerHTML = list.map(renderRoomItem).join('');
// }
//
// function renderRoomItem(room) {
//     return `<li>${room}</li>`;
// }
//
// function updateDocumentTitle(count) {
//     document.title = `대기실: 참가 가능한 방 ${count}개`;
// }
//
// function createNewRoom() {
//     const newRoomInput = document.getElementById('new-room-input');
//     const newRoomName = newRoomInput.value;
//     if (newRoomName) {
//         socket.send(JSON.stringify({ type: 'create_room', name: newRoomName }));
//     }
// }
//
// function handleRoomList(event) {
//     const list = JSON.parse(event.data);
//     console.log(list);
//     renderRoomList(list);
//     updateDocumentTitle(list.length);
// }
//
// socket.addEventListener('message', handleRoomList);
//
// socket.addEventListener('open', () => {
//     ParentToMessage({ type: 'room_list' });
// });

function handleNewRoom(event){
    event.preventDefault();
    const name = event.target.roomname.value;
    event.target.roomname.value = "";
    if (name.length == 0) {
        return;
    }
    const joinRoomMessage = {type: 'room_new', name: name};
    ParentToMessage(joinRoomMessage);
}

function RoomList(props) {
    const roomList = props.roomList;
    const ul = document.querySelector('#room-list-container');
    ul.innerHTML = '';
    roomList.forEach(function (roomItem) {
        const li = RoomItem(roomItem);
        ul.appendChild(li);
    });
}
function RoomItem(room) {
    const handleEnterRoom = () => {
        // socket.emit("room_enter", room.name);
        console.log("Entering room:", room.name);
    };

    const li = document.createElement('li');
    li.className = 'room-list__item';

    const p = document.createElement('p');
    p.className = 'room-list__name';
    p.textContent = room.name;

    const button = document.createElement('button');
    button.className = 'room-list__enter';
    button.textContent = '입장하기';
    button.addEventListener('click', handleEnterRoom);

    li.appendChild(p);
    li.appendChild(button);

    return li;
}
