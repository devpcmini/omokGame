const BOARD_OFFSET = 3.62;
const BOARD_SPACE = 5.14;
let inBoard = false;
let myTurn = false;
let coord = {};
let publicRoom = [];
let broadMessage = [];
let isFirst = true;
let myId;

//parent에서 message 받는 이벤트 리스너 
window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        switch (receivedMessage.type) {
            case 'room_list' : //방 목록 조회
                RoomList({ roomList: receivedMessage.data.map(room => ({ name: room.name }))});
                break;
            case 'player_select' : //해당 player turn 지정
                myTurn = true;
                OmokBoard();
                break;
            case 'player_change' : 
                myTurn = false;
                publicRoom[1] = receivedMessage.data.blackPlayer;
                publicRoom[2] = receivedMessage.data.whitePlayer;
                if (!(publicRoom[1] == "" || publicRoom[1] == null) &&
                    !(publicRoom[2] == "" || publicRoom[2] == null)) {
                    publicRoom[3] = [];
                }
                OmokBoard();
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                if(publicRoom[1] != '' && publicRoom[2] != '') {
                    if(myId == publicRoom[1]) {
                        document.querySelector('.startscreen').style.display = '';
                    }
                }
                break;
            case 'room_enter' : //방 입장
                isFirst = true;
                broadMessage = [];
                publicRoom = [receivedMessage.data.name,
                    receivedMessage.data.blackPlayer == null ? "" : receivedMessage.data.blackPlayer,
                    receivedMessage.data.whitePlayer == null ? "" : receivedMessage.data.whitePlayer,
                    receivedMessage.data.takes == null ? [] : receivedMessage.data.takes
                ];
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'message' : //메세지 받기
                broadMessage.push(receivedMessage.data);
                if(isFirst && receivedMessage.data.indexOf('[입장] => ') > -1){
                    isFirst = false;
                    myId = receivedMessage.data.replace("[입장] => ","");
                }
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'player_selected' : //오목판 reload
                publicRoom[3].push(receivedMessage.data);
                OmokBoard();
                break;
            case 'game_end' : //게임 종료
                GameEndScreen(receivedMessage.data);
                break;
        }
    }
});

//parent한테 메시지 보내는 함수
function ParentToMessage(message){
    window.parent.postMessage(message);
}

//message 이벤트를 받아 쌓는 함수
function MessageLine(msg) {
    return msg + "<br />";
}

//방 나가기 버튼 클릭 시 발생 함수
function leaveRoom(){
    document.querySelector('.exitscreen').style.display = '';
}

//관전하기 버튼 클릭 시 발생 함수
function moveSpectator() {
    ParentToMessage({type: 'player_change', data: 'spectator'});
}

//방 만들기 버튼 클릭 시 발생 함수
function handleNewRoom(event){
    event.preventDefault();
    const name = event.target.roomname.value;
    event.target.roomname.value = "";
    if (name.length == 0) {
        window.parent.document.querySelector('.alertscreen__text').innerText = '방 이름을 입력해주세요.';
        window.parent.document.querySelector('.alertscreen').style.display = '';
        return;
    }
    const joinRoomMessage = {type: 'room_new', name: name};
    ParentToMessage(joinRoomMessage);
}

//방 목록 생성 함수
function RoomList(props) {
    const roomList = props.roomList;
    const ul = document.querySelector('#room-list-container');
    ul.innerHTML = '';
    roomList.forEach(function (roomItem) {
        const li = RoomItem(roomItem);
        ul.appendChild(li);
    });
}

//방 요소 생성 함수
function RoomItem(room) {
    const handleEnterRoom = () => {
        const joinRoomMessage = {type: 'room_enter', name: room.name};
        ParentToMessage(joinRoomMessage);
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

//방 입장 시 오목판 우측에 요소 생성 함수
function GamePanel(roomname, blackPlayer, whitePlayer) {
    const gamePanel = document.querySelector(".game-panel");
    gamePanel.innerHTML = `
      <div class="game-panel__main">
        <h3 class="game-panel__title">${roomname}</h3>
        <div class="game-panel__players">
          <div class="game-panel__player">
            <h4 class="game-panel__playercolor game-panel__playercolor--black">
              Black
            </h4>
            <div class="game-panel__playerinfo">
              ${
                blackPlayer == "" || blackPlayer == null
                    ? `<button class="game-panel__playerselect" onclick="blackPlayerCallback()">참가</button>`
                    : `<p class="game-panel__playername">${blackPlayer}</p>`
                }
            </div>
          </div>
          <div class="game-panel__player">
            <h4 class="game-panel__playercolor game-panel__playercolor--white">White</h4>
            <div class="game-panel__playerinfo">
              ${
                  whitePlayer == "" || whitePlayer == null
                      ? `<button class="game-panel__playerselect" onclick="whitePlayerCallback()">참가</button>`
                      : `<p class="game-panel__playername">${whitePlayer}</p>`
              }
            </div>
          </div>
        </div>
        <div class="game-panel__message">
          <p>${broadMessage.map(MessageLine).join("")}</p>
        </div>
        <button class="game-start__button" onclick="gameStart()">게임시작</button>
      </div>
      <div class="game-panel__buttons">
        <button class="game-panel__button" onclick="moveSpectator()">관전하기</button>
        <button class="game-panel__button" onclick="leaveRoom()">방 나가기</button>
      </div>
    `;
}

//흑돌 플레이어로 참가
function blackPlayerCallback() {
    ParentToMessage({type: 'player_change', data : 'black'});
}

//백돌 플레이어로 참가
function whitePlayerCallback() {
    ParentToMessage({type: 'player_change', data : 'white'});
}

//오목판 안에 마우스 들어왔을때 이벤트
function handleBoardEnter() {
    inBoard = true;
}

//오목판 안에 마우스 나갔을때 이벤트
function handleBoardLeave() {
    inBoard = false;
}

//오목판 안에서 마우스 이동했을때 이벤트
function handleBoardMove(thisCoord) {
}

//오목판 클릭 시 이벤트
function handleBoardSelect(thisCoord) {
    myTurn = false;
    if (publicRoom[3].find((c) => c.x === thisCoord.x && c.y === thisCoord.y) !== undefined) {
        return;
    } else {
        coord = thisCoord;
    }
    ParentToMessage({type: 'player_selected', data : coord});
}

//오목판 Load 함수
function OmokBoard() {
    const omokBoard = document.querySelector(".omokboard");
    omokBoard.innerHTML = '';
    if(myTurn) {
        omokBoard.appendChild(CoordSelectArea(handleBoardEnter,handleBoardLeave,handleBoardSelect));
    }
    publicRoom[3].map((takes, index) => {
        omokBoard.appendChild(createStone([index % 2 == 0 ? "black" : "white"],takes.x, takes.y));
    });
    if(publicRoom[3].length > 0){
        omokBoard.appendChild(createStone(["prev"],publicRoom[3][publicRoom[3].length-1].x, publicRoom[3][publicRoom[3].length-1].y));
    }
}

//오목돌 생성 함수
function createStone(type, x, y ) {
    let material = "";
    type.forEach((m) => {
        switch (m) {
            case "black":
                material += " omokboard__stone--black";
                break;
            case "white":
                material += " omokboard__stone--white";
                break;
            case "hint":
                material += " omokboard__stone--hint";
                break;
            case "prev":
                material += " omokboard__stone--prev";
                break;
        }
    });

    const stoneElement = document.createElement("div");
    stoneElement.className = `omokboard__stone ${material}`;
    stoneElement.style.left = `${x * BOARD_SPACE + BOARD_OFFSET}%`;
    stoneElement.style.top = `${y * BOARD_SPACE + BOARD_OFFSET}%`;
    stoneElement.setAttribute("key", `${x}${y}`);
    return stoneElement;
}

//오목돌 놓는 좌표 구하는 함수
function getCoord(event) {
    let coordX = 0;
    let coordY = 0;

    const percentX = (event.offsetX * 100.0) / event.target.clientWidth;
    const percentY = (event.offsetY * 100.0) / event.target.clientHeight;

    coordX = parseInt((percentX - BOARD_OFFSET) / BOARD_SPACE + 0.5);
    coordY = parseInt((percentY - BOARD_OFFSET) / BOARD_SPACE + 0.5);

    if (coordX < 0) {
        coordX = 0;
    }
    if (coordY < 0) {
        coordY = 0;
    }
    if (coordX > 18) {
        coordX = 18;
    }
    if (coordY > 18) {
        coordY = 18;
    }

    return {
        x: coordX,
        y: coordY,
    };
}

//오목판에 이벤트 등록 함수
function CoordSelectArea(onBoardEnter,onBoardLeave,onBoardSelect) {
    function onMouseEnter() {
        onBoardEnter();
    }
    function onMouseLeave() {
        onBoardLeave();
    }
    function onMouseClick(event) {
        onBoardSelect(getCoord(event));
    }
    const omokboardCoord = document.createElement('div');
    omokboardCoord.className = "omokboard__coord";
    omokboardCoord.addEventListener("mouseenter", onMouseEnter);
    omokboardCoord.addEventListener("mouseleave", onMouseLeave);
    omokboardCoord.addEventListener("click", onMouseClick);

    return omokboardCoord;
}

//게임 승자 발생 시 팝업 호출 함수
function GameEndScreen(winner){
    const text = `${winner} is winner!`;
    document.querySelector('.endscreen').style.display = '';
    document.querySelector('.endscreen__text').innerText = text;
}

//게임 승자 팝업 닫기 함수
function onGameEnd(){
    document.querySelector('.endscreen').style.display = 'none';
}

//게임 시작 함수
function onGameStart(){
    document.querySelector('.startscreen').style.display = 'none';
}

//게임 시작 팝업 취소 함수
function onGameStartWait(){
    document.querySelector('.startscreen').style.display = 'none';
    ParentToMessage({type: 'room_reset'});
}

//방 나가기 취소 함수
function onGameExitCancel(){
    document.querySelector('.exitscreen').style.display = 'none';
}

//방 나가기 함수
function onGameExit(){
    document.querySelector('.exitscreen').style.display = 'none';
    ParentToMessage({type: 'room_leave'});
}

window.addEventListener('keydown',(event)=>{
    const exitscreen = document.querySelector('.exitscreen').style.display;
    const startscreen = document.querySelector('.startscreen').style.display;
    const endscreen = document.querySelector('.endscreen').style.display;
    if(event.code == 'Enter'){
        if(exitscreen == ""){
            onGameExit();
        } else if(startscreen == ""){
            onGameStart();
        } else if(endscreen == ""){
            onGameEnd();
        }
    } else if(event.code == 'Escape'){
        if(exitscreen == ""){
            onGameExitCancel()();
        } else if(startscreen == ""){
            onGameStartWait();
        }
    }
});