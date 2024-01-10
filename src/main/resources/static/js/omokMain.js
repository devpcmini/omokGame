let inBoard = false;
let myTurn = false;
let coord = {};
let isGameEnd = false;
let publicRoom = [];
let broadMessage = [];

window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        switch (receivedMessage.type) {
            case 'room_list' :
                RoomList({ roomList: receivedMessage.data.map(room => ({ name: room.name }))});
                break;
            case 'player_select' :
                myTurn = true;
                break;
            case 'player_change' :
                myTurn = false;
                publicRoom[1] = receivedMessage.data.blackPlayer;
                publicRoom[2] = receivedMessage.data.whitePlayer;
                if (!(publicRoom[1] == "" || publicRoom[1] == null) &&
                    !(publicRoom[2] == "" || publicRoom[2] == null)) {
                    publicRoom[3] = [];
                }
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'room_enter' :
                broadMessage = [];
                publicRoom = [receivedMessage.data.name,
                    receivedMessage.data.blackPlayer,
                    receivedMessage.data.whitePlayer,
                    receivedMessage.data.takes
                ];
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'message' :
                broadMessage.push(receivedMessage.data);
                GamePanel(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
        }
    }
});

function ParentToMessage(message){
    window.parent.postMessage(message);
}

function MessageLine(msg) {
    return msg + "<br />";
}

function leaveRoom(){
    ParentToMessage({type: 'room_leave'});
}

function moveSpectator() {
    ParentToMessage({type: 'player_change', data: 'spectator'});
}

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
      </div>
      <div class="game-panel__buttons">
        <button class="game-panel__button" onclick="moveSpectator()">관전하기</button>
        <button class="game-panel__button" onclick="leaveRoom()">방 나가기</button>
      </div>
    `;
}

function blackPlayerCallback() {
    ParentToMessage({type: 'player_change', data : 'black'});
}

function whitePlayerCallback() {
    ParentToMessage({type: 'player_change', data : 'white'});
}

function handleBoardEnter() {
    inBoard = true;
}

function handleBoardLeave() {
    inBoard = false;
}

function handleBoardMove(thisCoord) {
    if (publicRoom[3].find((c) => c.x === thisCoord.x && c.y === thisCoord.y) === undefined) {
        coord = thisCoord;
    }
}

function handleBoardSelect() {
    myTurn = false;
    console.log(`Select [${coord.x},${coord.y}]`);
    ParentToMessage({type: 'player_selected', data : coord});
}

function OmokBoard(takes) {
    publicRoom[3] = takes;
    const omokBoard = document.querySelector(".omokboard");
    omokBoard.innerHTML = `
      ${myTurn ? CoordSelectArea(handleBoardEnter,handleBoardMove,handleBoardLeave,handleBoardSelect) : ""}
      ${publicRoom[3].map((takes, index) => MemoriedStone(index))}
      ${publicRoom[3].length > 0 ? MemoriedStone(publicRoom[3].length - 1, true) : ""}
      ${myTurn && inBoard ? MemoriedStone(publicRoom[3].length % 2 === 0 ? "black" : "white", "hint") : ""}
    `;
}

function createStone({ type, x, y }) {
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

const MemoriedStone = (()=> {
    const memoCache = {};
    function memoizeStone(props) {
        const key = JSON.stringify(props);
        if (memoCache[key]) {
            return memoCache[key];
        } else {
            const stoneElement = createStone(props);
            memoCache[key] = stoneElement;
            return stoneElement;
        }
    }
    return memoizeStone;
})();

function CoordSelectArea(onBoardEnter,onBoardMove,onBoardLeave,onBoardSelect) {
    function getCoord(event) {
        let coordX = 0;
        let coordY = 0;

        if (!isMobile) {
            const percentX = (event.offsetX * 100.0) / event.target.clientWidth;
            const percentY = (event.offsetY * 100.0) / event.target.clientHeight;

            coordX = parseInt((percentX - BOARD_OFFSET) / BOARD_SPACE + 0.5);
            coordY = parseInt((percentY - BOARD_OFFSET) / BOARD_SPACE + 0.5);
        } else {
            const bcr = event.target.getBoundingClientRect();
            const x = event.touches[0].clientX - bcr.x;
            const y = event.touches[0].clientY - bcr.y;

            const percentX = (x * 100.0) / event.target.clientWidth;
            const percentY = (y * 100.0) / event.target.clientHeight;
            coordX = parseInt((percentX - BOARD_OFFSET) / BOARD_SPACE + 0.5);
            coordY = parseInt((percentY - BOARD_OFFSET) / BOARD_SPACE - 1.5);
        }

        if (coordX < 0) coordX = 0;
        if (coordY < 0) coordY = 0;

        if (coordX > 18) coordX = 18;
        if (coordY > 18) coordY = 18;

        return {
            x: coordX,
            y: coordY,
        };
    }

    function onMouseEnter() {
        onBoardEnter();
    }

    function onMouseMove(event) {
        onBoardMove(getCoord(event));
    }

    function onMouseLeave() {
        onBoardLeave();
    }

    function onMouseClick() {
        onBoardSelect();
    }

    function onTouchStart(event) {
        onBoardEnter();
        onBoardMove(getCoord(event));
    }

    function onTouchMove(event) {
        onBoardMove(getCoord(event));
    }

    function onTouchEnd(event) {
        onBoardLeave();
        onBoardSelect();
    }

    const omokboardCoord = document.createElement('div');
    omokboardCoord.className = "omokboard__coord";
    omokboardCoord.addEventListener("mouseenter", onMouseEnter);
    omokboardCoord.addEventListener("mousemove", onMouseMove);
    omokboardCoord.addEventListener("mouseleave", onMouseLeave);
    omokboardCoord.addEventListener("click", onMouseClick);
    omokboardCoord.addEventListener("touchstart", onTouchStart);
    omokboardCoord.addEventListener("touchmove", onTouchMove);
    omokboardCoord.addEventListener("touchend", onTouchEnd);

    return omokboardCoord;
}